package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.CouponCodeQuery;
import com.tianji.promotion.service.IExchangeCodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 兑换码 前端控制器
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/codes")
@Api(tags = "兑换码相关接口")
@RequiredArgsConstructor

public class ExchangeCodeController {

    private final IExchangeCodeService exchangeCodeService;
    @GetMapping("/page")
    @ApiOperation("分页查询优惠券码")
    public PageDTO<?> getCouponCodePage(CouponCodeQuery query) {
        return exchangeCodeService.getCouponCodePage(query);
    }
}
