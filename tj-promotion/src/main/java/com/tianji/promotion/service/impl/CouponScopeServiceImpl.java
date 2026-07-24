package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.CouponScopeMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券作用范围信息 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
@Service
public class CouponScopeServiceImpl extends ServiceImpl<CouponScopeMapper, CouponScope> implements ICouponScopeService {

    // 更新优惠券作用范围
    @Override
    @Transactional
    public void updateCouponScopes(Long id, List<Long> scopes) {
        deleteCouponScopes(id);
        if (CollUtils.isEmpty(scopes)) return;
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setCouponId(id).setBizId(bizId))
                .collect(Collectors.toList());
        saveBatch(list);
    }

    @Override
    public void deleteCouponScopes(Long id) {
        remove(new LambdaQueryWrapper<CouponScope>().eq(CouponScope::getCouponId, id));
    }
}
