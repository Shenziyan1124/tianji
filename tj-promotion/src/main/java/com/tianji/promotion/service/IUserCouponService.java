package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-27
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long couponId);

    // 校验并创建用户优惠券
    @Transactional
    void checkAndCreateUserCoupon(Coupon coupon, Long userId);

    void exchangeCoupon(String code);
}
