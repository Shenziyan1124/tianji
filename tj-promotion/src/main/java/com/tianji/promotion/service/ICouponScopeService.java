package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.CouponScope;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 优惠券作用范围信息 服务类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
public interface ICouponScopeService extends IService<CouponScope> {

    void updateCouponScopes(Long id, List<Long> scopes);

    void deleteCouponScopes(Long id);
}
