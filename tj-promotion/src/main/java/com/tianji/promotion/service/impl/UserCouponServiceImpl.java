package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockType;
import com.tianji.promotion.utils.RedisLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-27
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService exchangeCodeService;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final RabbitMqHelper mqHelper;

    // 领取优惠券
    @Override
    @Lock(name = "lock:coupon:#{couponId}")
    public void receiveCoupon(Long couponId) {
        Long userId = UserContext.getUser();

        // 1. 查询优惠券
        //Coupon coupon = couponMapper.selectById(couponId);
        // 改成mq通知的方式
        Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) throw new BadRequestException("优惠券不存在");

        // 2. 校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime()))
            throw new BadRequestException("优惠券不在发放时间内");
        // 3. 判断库存是否充足
        // 获取一次从redis的库存-1,之前是从数据库查的
        if (coupon.getTotalNum() <= 0)
            throw new BadRequestException("优惠券库存不足");

        // 4. 校验并创建用户优惠券
        // 方式一: 同步锁
        // synchronized (userId.toString().intern()) { // 以用户ID为锁
        //    IUserCouponService o = (IUserCouponService) AopContext.currentProxy();
        //    o.checkAndCreateUserCoupon(coupon, userId);
        // }
        //        // 4.1 创建锁
        //        String key = "lock:user:uId:" + userId;
        //        // 方式二: 自定义redis锁
        //        //RedisLock redisLock = new RedisLock(key, redisTemplate);
        //        // 方式三: redisson锁
        //        RLock lock = redissonClient.getLock(key);
        //
        //        // 方式二: 4.2 获取锁
        //        //Boolean isLock = redisLock.tryLock(5, TimeUnit.SECONDS);
        //
        //        // 方式三: 4.2 获取锁
        //        boolean isLock = lock.tryLock();
        //
        //        if (!isLock) throw new BizIllegalException("请求太频繁");
        //
        //        try {
        //            // 4.3 获取成功,开始业务
        //            IUserCouponService o = (IUserCouponService) AopContext.currentProxy();
        //            o.checkAndCreateUserCoupon(coupon, userId);
        //        } finally {
        //            // 方式二: 4.4 释放锁
        //            //redisLock.unlock();
        //
        //            // 方式三: 4.4 释放锁
        //            lock.unlock();
        //        }
        //        IUserCouponService o = (IUserCouponService) AopContext.currentProxy();
        //        o.checkAndCreateUserCoupon(coupon, userId);


        // 4. redis获取,mq通知
        // 4. 校验每人领取数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        // 4. 校验限领数量
        if (count > coupon.getUserLimit()) {
            throw new BadRequestException("超出每人领取数量");
        }
        // 4. 扣减优惠券库存
        redisTemplate.opsForHash().increment(
                PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId,
                "totalNum",
                -1
        );
        // 5. 发送mq消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setCouponId(couponId);
        uc.setUserId(userId);
        mqHelper.send(
                MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                uc
        );


    }

    private Coupon queryCouponByCache(Long couponId) {
        // 1. 准备key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 2. 查询
        Map<Object, Object> objectMap = redisTemplate.opsForHash().entries(key);
        if (objectMap.isEmpty()) return null;
        // 3. 反序列化,得到的map转成po
        return BeanUtils.mapToBean(objectMap, Coupon.class, false, CopyOptions.create());
    }

    // 校验并创建用户优惠券

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        Long userId = uc.getUserId();
        // 1. 判断超出每人限领数量
        // 1.1 统计当前用户已领取的优惠券数量
        //        Integer count = lambdaQuery()
        //                .eq(UserCoupon::getCouponId, coupon.getId())
        //                .eq(UserCoupon::getUserId, userId)
        //                .count();
        //        if (count != null && count >= coupon.getUserLimit())
        //            throw new BadRequestException("优惠券超出每人限领数量");

        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }


        // 2. 更新优惠券数量+1
        int i = couponMapper.incrIssueNum(coupon.getId());
        if (i == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }

        // 3. 插入数据库
        saveUserCoupon(coupon, userId);

        if (uc.getSerialNum() != null) {
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, userId)
                    .eq(ExchangeCode::getId, uc.getSerialNum())
                    .update();
        }

    }

    // 保存用户优惠券
    private void saveUserCoupon(Coupon coupon, Long userId) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());

        LocalDateTime beginTime = coupon.getTermBeginTime();
        LocalDateTime endTime = coupon.getTermEndTime();
        // 发放优惠券有是按天数发放,有是按开始结束日期发放,按天发放没有开始时间 ==null,所以需要单独判断一下,来设置开始结束时间
        if (beginTime == null) {
            beginTime = LocalDateTime.now();
            endTime = beginTime.plusDays(coupon.getTermDays());
        }

        userCoupon.setTermBeginTime(beginTime);
        userCoupon.setTermEndTime(endTime);

        save(userCoupon);
    }

    // 兑换码兑换优惠券[
    @Override
    // @Transactional
    @Lock(name = "lock:coupon:#{T(com.tianji.common.utils.UserContext).getUser()}")
    public void exchangeCoupon(String code) {

        // 1. 校验解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2. 查看看兑换码是否已经兑换 bitmap查询是否兑换 setbit
        // 2.1 查询兑换码是否已经兑换,会返回true就是已经兑换过了,会返回修改前的值
        boolean isExchange = exchangeCodeService.updateExchangeMark(serialNum, true);
        if (isExchange) throw new BizIllegalException("兑换码已经兑换过");

        try {
            // 3. 查询兑换码
            //ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            Long couponId = exchangeCodeService.exchangeTargetId(serialNum);
            if (couponId == null)
                throw new BizIllegalException("兑换码不存在");
            Coupon coupon = queryCouponByCache(couponId);

            // 4. 是否过期
            LocalDateTime now = LocalDateTime.now();
            if (coupon != null) {
                if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime())) {
                    throw new BizIllegalException("兑换码已经过期");
                }
            }

            // 5. 校验限领数量
            // 6. 更新优惠券已发放的数量+1

            Long userId = UserContext.getUser();
            String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
            Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
            if (coupon != null && count > coupon.getUserLimit()) {
                throw new BadRequestException("超出领取数量");
            }

            redisTemplate.opsForHash().increment(
                    PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId,
                    "totalNum",
                    -1
            );

            // new7.发送MQ消息通知
            UserCouponDTO uc = new UserCouponDTO();
            uc.setUserId(userId);
            uc.setCouponId(couponId);
            uc.setSerialNum((int) serialNum);
            mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, uc);

            // 7. 新增一个用户券
            // 8. 创建用户券 添加锁
            //            synchronized (userId.toString().intern()) {
            //                IUserCouponService o = (
            //                        IUserCouponService) AopContext.currentProxy();
            //                o.exchangeCouponWithTransaction(coupon, userId, exchangeCode);
            //            }

        } catch (Exception e) {
            // 出现异常,将兑换码状态回滚
            exchangeCodeService.updateExchangeMark(serialNum, false);
            throw e;
        }
    }

    // 兑换码兑换优惠券 添加锁
    @Transactional
    @Override
    public void exchangeCouponWithTransaction(Coupon coupon, Long userId, ExchangeCode exchangeCode) {
        //checkAndCreateUserCoupon(coupon, userId);
        // 8. 更新兑换码状态 数据库和redismap都更新  setbit
        exchangeCodeService.lambdaUpdate()
                .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                .set(ExchangeCode::getUserId, userId)
                .eq(ExchangeCode::getId, exchangeCode.getId())
                .update();
    }

    // TODO 分页查询用户优惠券
    @Override
    public PageDTO<CouponPageVO> pageUserCoupons(UserCouponQuery query) {
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .eq(query.getStatus() != null, UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) return PageDTO.empty(page);

        List<CouponPageVO> couponPageVOS = BeanUtils.copyList(records, CouponPageVO.class);

        return PageDTO.of(page, couponPageVOS);
    }

    // 核销优惠券
    @Override
    @Transactional
    public void useCoupon(List<Long> userCouponIds) {
        List<UserCoupon> userCoupons = listByIds(userCouponIds);
        if (CollUtils.isEmpty(userCoupons)) throw new BizIllegalException("优惠券不存在");

        // 过滤出未使用的优惠券
        List<UserCoupon> collect = userCoupons.stream()
                .filter(uc -> {
                    if (uc == null) return false;

                    if (uc.getStatus() != UserCouponStatus.UNUSED) return false;

                    LocalDateTime now = LocalDateTime.now();
                    //return !now.isAfter(uc.getTermEndTime()) || now.isBefore(uc.getTermBeginTime());
                    return !now.isBefore(uc.getTermBeginTime()) && !now.isAfter(uc.getTermEndTime());
                })
                .map(uc -> {
                    UserCoupon userCoupon = new UserCoupon();
                    userCoupon.setId(uc.getId());
                    userCoupon.setStatus(UserCouponStatus.USED);
                    return userCoupon;
                })
                .collect(Collectors.toList());

        // 没有可核销的券,直接返回
        if (CollUtils.isEmpty(collect)) return;

        // 批量更新
//        boolean success = updateBatchById(collect);
//        if (!success) return;

        // 只对真正核销成功的券增加使用数量(collect里的对象只有id和status,需要回到userCoupons里取couponId)
        Set<Long> updatedIds = collect.stream().map(UserCoupon::getId).collect(Collectors.toSet());

        boolean success = lambdaUpdate()
                .in(UserCoupon::getId, updatedIds)
                .eq(UserCoupon::getStatus, UserCouponStatus.UNUSED)
                .set(UserCoupon::getStatus, UserCouponStatus.USED)
                .update();
        if (!success) return;

        List<Long> couponIds = userCoupons.stream()
                .filter(uc -> updatedIds.contains(uc.getId()))
                .map(UserCoupon::getCouponId)
                .collect(Collectors.toList());
        int c = couponMapper.incrUsedNum(couponIds,1);
        if (c < 1){
            throw new DbException("更新优惠券使用数量失败！");
        }
    }

}
