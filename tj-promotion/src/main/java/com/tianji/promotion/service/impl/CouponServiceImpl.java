package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.promotion.config.PromotionConfig;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponCodeQuery;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;
    private final IExchangeCodeService exchangeCodeService;
    private final IUserCouponService userCouponService;
    private final StringRedisTemplate redisTemplate;

    // 保存优惠券
    @Override
    public void saveCoupon(CouponFormDTO dto) {
        // 1. 保存优惠券信息
        // 1.1 转po
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);

        if (!dto.getSpecific()) return; // true有限定范围 false无,无限定范围直接返回
        Long couponId = coupon.getId();
        // 2. 保存限定范围
        List<Long> scopes = dto.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("优惠券限定范围不能为空");
        }
        List<CouponScope> list = scopes.stream()
                .map(bizId ->
                        new CouponScope().setCouponId(couponId).setBizId(bizId)).collect(Collectors.toList());
        couponScopeService.saveBatch(list);
    }


    // 分页查询优惠券
    @Override
    public PageDTO<CouponPageVO> getCouponPage(CouponQuery query) {
        String name = query.getName();
        Integer status = query.getStatus();
        Integer type = query.getType();
        // 1. 查询
        Page<Coupon> page = lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) return PageDTO.empty(page);

        // 2. 处理vo
        List<CouponPageVO> couponPageVOS = BeanUtils.copyList(records, CouponPageVO.class);
        // 3. 返回
        return PageDTO.of(page, couponPageVOS);
    }


    // 根据id查询优惠券
    @Override
    public CouponDetailVO getCouponById(Long id) {
        Coupon coupon = lambdaQuery()
                .eq(id != null, Coupon::getId, id)
                .one();
        return BeanUtils.copyBean(coupon, CouponDetailVO.class);
    }

    // 更新优惠券
    @Override
    public void updateCoupon(CouponFormDTO dto) {
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        coupon.setId(dto.getId());
        updateById(coupon);
        couponScopeService.updateCouponScopes(dto.getId(), dto.getScopes());
    }

    // 删除优惠券
    @Override
    public void deleteCoupon(Long id) {
        removeById(id);
        couponScopeService.deleteCouponScopes(id);
    }

    // 发放优惠券
    @Override
    @Transactional
    public void beginIssueCoupon(CouponIssueFormDTO dto) {
        // 1. 查询优惠券
        Coupon coupon = getById(dto.getId());
        if (coupon == null) throw new BadRequestException("优惠券不存在!");
        // 2. 判断状态是否是暂停或待发放
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE){
            throw new BadRequestException("优惠券状态不允许发放");
        }
        // 3. 判断是否立即发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now(); // 当前时间
        // 开始时间为null 或者 开始时间小于等于当前时间 Before小于 after是大于 取反就是小于等于
        // 代表立刻发放
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);

        // 4. 更新状态
        // 4.1 拷贝属性到po
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2 判断是否立即发放还是定时发放
        if (isBegin){
            c.setStatus(CouponStatus.ISSUING);
            c.setIssueBeginTime(now);
        }else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        // 4.3 更新到数据库
        updateById(c);
        // 4.4 添加缓存, 立即发放的时候添加缓存
        if (isBegin){
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        // 5. 判断是否需要生成兑换码,优惠券类型必须是兑换码,优惠券状态必须是待发放
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT){
            coupon.setIssueEndTime(c.getIssueEndTime());
            exchangeCodeService.asyncGenerateCodes(coupon);
        }
    }

    private void cacheCouponInfo(Coupon coupon) {
        Map<String, String> map = new HashMap<>();
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum",String.valueOf(coupon.getTotalNum()));
        map.put("userLimit",String.valueOf(coupon.getUserLimit()));

        redisTemplate
                .opsForHash().putAll(PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId(),map);
    }

    // 暂停优惠券
    @Override
    @Transactional
    public void pauseCoupon(Long id) {
        Coupon coupon = getById(id);
        if (coupon == null)
            throw new BadRequestException("优惠券不存在!");

        if (coupon.getStatus() != CouponStatus.ISSUING)
            throw new BadRequestException("优惠券状态不允许暂停");

        coupon.setStatus(CouponStatus.PAUSE);
        updateById(coupon);

        // 4. 删除缓存
        redisTemplate.delete(PromotionConstants.COUPON_CACHE_KEY_PREFIX + id);
    }

    // 获取用户优惠券列表
    @Override
    public List<CouponVO> getUserCouponList() {
        // 1. 查询发放中的优惠券列表
        List<Coupon> list = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(list)) return CollUtils.emptyList();

        // 2. 统计当前user的已经领取优惠券数量
        List<Long> couponIds = list.stream().map(Coupon::getId).collect(Collectors.toList());
        // 2.1 查询当前user的已经领取优惠券数据
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .in(UserCoupon::getCouponId, couponIds)
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .list();
        // 2.2 统计当前user对优惠券已经领取的数量,进行分组
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3 统计当前user对优惠券已经领取并未使用的数量,分组
        Map<Long, Long> unIssuedMap = userCoupons.stream()
                .filter(userCoupon -> userCoupon.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 3. 处理vo
        List<CouponVO> voList = new ArrayList<>(list.size());
        for (Coupon c : list) {
            // 3.1 po转vo
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            voList.add(vo);
            // 3.2 是否可以领取: 已经被领取的数量 < 总数量 && 用户已领取的数量 < 每人限领的数量
            vo.setAvailable(
                    c.getIssueNum() < c.getTotalNum()
                    && issuedMap.getOrDefault(c.getId(),0L) < c.getUserLimit()
            );
            // 3.3 是否可以使用: 当前用户是否存在已经领取&&未使用的优惠券
            vo.setReceived(unIssuedMap.getOrDefault(c.getId(), 0L) > 0);
        }
        return voList;
    }


}
