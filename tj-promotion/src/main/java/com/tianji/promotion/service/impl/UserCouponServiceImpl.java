package com.tianji.promotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    // 领取优惠券
    @Override

    public void receiveCoupon(Long couponId) {
        Long userId = UserContext.getUser();

        // 1. 查询优惠券
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) throw new BadRequestException("优惠券不存在");

        // 2. 校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime()))
            throw new BadRequestException("优惠券不在发放时间内");
        // 3. 判断库存是否充足
        if (coupon.getIssueNum() >= coupon.getTotalNum())
            throw new BadRequestException("优惠券库存不足");

        // 4. 校验并创建用户优惠券
        synchronized (userId.toString().intern()) { // 以用户ID为锁
            IUserCouponService o = (IUserCouponService) AopContext.currentProxy();
            o.checkAndCreateUserCoupon(coupon, userId);
        }
    }

    // 校验并创建用户优惠券
    @Transactional
    @Override
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId) {
        // 1. 判断超出每人限领数量
        // 1.1 统计当前用户已领取的优惠券数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getCouponId, coupon.getId())
                .eq(UserCoupon::getUserId, userId)
                .count();

        if (count != null && count >= coupon.getUserLimit())
            throw new BadRequestException("优惠券超出每人限领数量");

        // 2. 更新优惠券数量+1
        int i = couponMapper.incrIssueNum(coupon.getId());
        if (i == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }

        // 3. 插入数据库
        saveUserCoupon(coupon, userId);

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

    // 兑换码兑换优惠券
    @Override
    @Transactional
    public void exchangeCoupon(String code) {

        // 1. 校验解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2. 查看看兑换码是否已经兑换 bitmap查询是否兑换 setbit
        // 2.1 查询兑换码是否已经兑换,会返回true就是已经兑换过了,会返回修改前的值
        boolean isExchange = exchangeCodeService.updateExchangeMark(serialNum, true);
        if (isExchange) throw new BizIllegalException("兑换码已经兑换过");

        try {
            // 3. 查询兑换码
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if (exchangeCode == null)
                throw new BizIllegalException("兑换码不存在");
            // 4. 是否过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(exchangeCode.getExpiredTime()))
                throw new BizIllegalException("兑换码已经过期");
            // 5. 校验限领数量
            // 6. 更新优惠券已发放的数量+1
            // 7. 新增一个用户券
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            Long userId = UserContext.getUser();
            checkAndCreateUserCoupon(coupon, userId);
            // 8. 更新兑换码状态 数据库和redismap都更新  setbit
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId, userId)
                    .eq(ExchangeCode::getId, exchangeCode.getId())
                    .update();
        } catch (Exception e) {
            // 出现异常,将兑换码状态回滚
            exchangeCodeService.updateExchangeMark(serialNum, false);
            throw e;
        }

    }
}
