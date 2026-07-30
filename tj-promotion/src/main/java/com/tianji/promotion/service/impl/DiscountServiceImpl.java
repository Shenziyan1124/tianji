package com.tianji.promotion.service.impl;


import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {

    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService couponScopeService;

    // 查询我的优惠券可用方案
    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {

        // 1. 查询我的所有可用优惠券
        Long userId = UserContext.getUser();
        List<Coupon> coupons = userCouponMapper.queryMyCoupons(userId);
        if (CollUtils.isEmpty(coupons)) return CollUtils.emptyList();

        // 2. 初筛 订单总价达到优惠券可用门槛
        // 2.1 计算订单总价
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        // 2.2 筛选可用券
        List<Coupon> availableCoupons = coupons.stream().filter(coupon ->
                        DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) return CollUtils.emptyList();

        // 3. 排列出所有方案
        // 3.1 细筛(找出每个优惠券可用的课程,判断课程总价是否达到优惠券的使用需求)
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCourse(availableCoupons,orderCourses);
        if (CollUtils.isEmpty(availableCouponMap)) return CollUtils.emptyList();
        // 3.2 排列组合
        availableCoupons =  new ArrayList<>(availableCouponMap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        // 3.3 添加单券方案
        for (Coupon coupon : availableCoupons){
            solutions.add(List.of(coupon));
        }

        // 4. 计算每种方案
        ArrayList<CouponDiscountDTO> list = new ArrayList<>(solutions.size());
        for (List<Coupon> solution : solutions){
            list.add(calculateSolutionDiscount(availableCouponMap,orderCourses,solution));
        }
        // 5. 筛选出最优方案
        return list;
    }

    private CouponDiscountDTO calculateSolutionDiscount(
            Map<Coupon, List<OrderCourseDTO>> couponMap,
            List<OrderCourseDTO> courses,
            List<Coupon> solution) {

        // 1. 初始化dto
        CouponDiscountDTO dto = new CouponDiscountDTO();
        // 2. 初始化折扣明细的映射,是从页面存入的courses
        Map<Long, Integer> detailMap = courses.stream()
                .collect(Collectors.toMap(OrderCourseDTO::getId, oc -> 0));
        // 3. 计算折扣
        for (Coupon coupon : solution){
            // 3.1 获取优惠券限定范围对应的课程
            List<OrderCourseDTO> availableCourses = couponMap.get(coupon);
            // 3.2 计算课程总价 课程原价-折扣明细
            int totalAmount = availableCourses.stream()
                    .mapToInt(oc -> oc.getPrice() - detailMap.get(oc.getId())).sum();
            // 3.3 判断是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) continue; // 券不可用,跳过
            // 3.4 计算优惠总金额
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            // 3.5 计算优惠明细
            calculateDiscountDetails(detailMap, availableCourses, totalAmount, discountAmount);
            // 3.6 更新dto
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(dto.getDiscountAmount() + discountAmount);
        }
        return dto;
    }

    private void calculateDiscountDetails(
            Map<Long, Integer> detailMap, List<OrderCourseDTO> courses,
            int totalAmount, int discountAmount) {
        //
        int times = 0;
        //
        int remainDiscount = discountAmount;

        for (OrderCourseDTO course : courses){

            times++; // 课程已计算的数量
            int discount = 0; // 课程的折扣金额

            if (times == courses.size()){
                // 是最后一个,总折扣金额 - 之前所有的折扣明细,最后剩下的就是最后一个的
                discount = remainDiscount;
            }else{
                // 计算折扣明细, 课程价格在总价中占的比例,乘总的折扣
                discount = discountAmount * course.getPrice() / totalAmount;
                remainDiscount -= discount;
            }

            // 更新折扣明细,
            detailMap.put(course.getId(), detailMap.get(course.getId()) + discount);
        }
    }


    private Map<Coupon, List<OrderCourseDTO>> findAvailableCourse(
            List<Coupon> coupons, List<OrderCourseDTO> courses) {

        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>();
        for (Coupon coupon : coupons){
            // 1. 找出优惠券可用的课程
            List<OrderCourseDTO> availableCourses = courses;
            if (coupon.getSpecific()){
                // 1.1 限定了课程的范围,查询券可用范围
                List<CouponScope> scopes = couponScopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId()).list();
                // 1.2 获取范围对应的id
                Set<Long> scopesIds = scopes.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                // 1.3 筛选课程
                availableCourses = courses.stream().filter(
                        course -> scopesIds.contains(course.getId())).collect(Collectors.toList());
            }
            // 如果没有可用的课程,跳出, 找下一圈
            if (CollUtils.isEmpty(availableCourses)){
                continue;
            }

            // 2. 计算课程总价
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();

            // 3. 判断是否可用
            if (DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon)){
                map.put(coupon, availableCourses);
            }

        }
        return map;
    }
}
