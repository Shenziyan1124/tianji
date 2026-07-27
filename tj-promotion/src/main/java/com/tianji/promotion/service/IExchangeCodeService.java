package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponCodeQuery;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateCodes(Coupon coupon);

    PageDTO<?> getCouponCodePage(CouponCodeQuery query);

    boolean updateExchangeMark(long serialNum, boolean b);
}
