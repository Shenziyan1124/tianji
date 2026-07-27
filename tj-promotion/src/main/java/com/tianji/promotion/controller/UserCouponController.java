package com.tianji.promotion.controller;


import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-07-27
 */
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
@Api(tags = "用户优惠券管理")
public class UserCouponController {
    private final IUserCouponService userCouponService;

    @PostMapping("/{couponId}/receive")
    @ApiOperation("领取优惠券")
    public void receiveCoupon(@PathVariable("couponId") Long couponId) {
        userCouponService.receiveCoupon(couponId);
    }

    @PostMapping("/{code}/exchange")
    @ApiOperation("兑换码兑换优惠券")
    public void exchangeCoupon(@PathVariable("code") String code) {
        userCouponService.exchangeCoupon(code);
    }
}
