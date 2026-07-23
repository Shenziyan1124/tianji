package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(@Valid CouponFormDTO dto);

    PageDTO<CouponPageVO> getCouponPage(CouponQuery query);
}
