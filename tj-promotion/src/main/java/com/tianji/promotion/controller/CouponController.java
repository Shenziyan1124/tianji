package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponCodeQuery;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Api(tags = "优惠券相关接口")
public class CouponController {

    private final ICouponService couponService;

    @PostMapping
    @ApiOperation("新增优惠券")
    public void saveCoupon(@RequestBody @Valid CouponFormDTO dto) {
        couponService.saveCoupon(dto);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询优惠券")
    public PageDTO<CouponPageVO> getCouponPage(CouponQuery query) {
        return couponService.getCouponPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询优惠券")
    public CouponDetailVO getCouponById(@PathVariable("id") Long id) {
        return couponService.getCouponById(id);
    }

    @PutMapping
    @ApiOperation("修改优惠券")
    public void updateCoupon(@RequestBody @Valid CouponFormDTO dto) {
        couponService.updateCoupon(dto);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除优惠券")
    public void deleteCoupon(@PathVariable("id") @Valid Long id) {
        couponService.deleteCoupon(id);
    }

    @PutMapping("/{id}/issue")
    @ApiOperation("发放优惠券")
    public void beginIssueCoupon(@RequestBody @Valid CouponIssueFormDTO dto) {
        couponService.beginIssueCoupon(dto);
    }

    @PutMapping("/{id}/pause")
    @ApiOperation("暂停优惠券")
    public void pauseCoupon(@PathVariable("id") @Valid Long id) {
        couponService.pauseCoupon(id);
    }
}
