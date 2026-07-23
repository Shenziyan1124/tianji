package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
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
        if (CollUtils.isEmpty(scopes)){
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
}
