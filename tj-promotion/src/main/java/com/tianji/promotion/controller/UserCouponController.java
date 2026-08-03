package com.tianji.promotion.controller;


import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final IDiscountService discountService;

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

    @GetMapping("/page")
    @ApiOperation("分页查询用户优惠券")
    public PageDTO<CouponPageVO> pageUserCoupons(UserCouponQuery query) {
        return userCouponService.pageUserCoupons(query);
    }


    @PostMapping("/available")
    @ApiOperation("查询我的优惠券可用方案")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourses) {
        return discountService.findDiscountSolution(orderCourses);
    }

    // feign tj-trade调用, 查询优惠券方案计算订单优惠明细,存入订单表
    @ApiOperation("查询优惠券方案计算订单优惠明细")
    @PostMapping("/discount")
    public CouponDiscountDTO queryDiscountDetailByOrder(@RequestBody OrderCouponDTO dto) {
        return discountService.queryDiscountDetailByOrder(dto);
    }

    @PutMapping("/use")
    @ApiOperation("核销优惠券")
    public void useCoupon(
            @ApiParam("用户优惠券id集合")
            @RequestParam("couponIds")
            List<Long> userCouponIds
    ) {
        userCouponService.useCoupon(userCouponIds);
    }

    @PutMapping("/refund")
    @ApiOperation("退还优惠券")
    public void refundCoupon(@ApiParam("用户优惠券id集合")
                             @RequestParam("couponIds")
                             List<Long> userCouponIds)
    {
        userCouponService.refundCoupon(userCouponIds);
    }


    @GetMapping("/rules")
    @ApiOperation("分页查询我的优惠券接口")
    public List<String> queryDiscountRules(
            @ApiParam("用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds){
        return userCouponService.queryDiscountRules(userCouponIds);
    }


}
