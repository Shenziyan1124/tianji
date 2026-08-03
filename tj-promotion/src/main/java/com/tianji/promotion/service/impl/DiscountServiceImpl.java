package com.tianji.promotion.service.impl;


import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {

    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService couponScopeService;
    private final Executor discountSolutionExecutor;

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
        List<CouponDiscountDTO> list = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        // 4.1 定义闭锁 解决并发访问
        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions){
            CompletableFuture.supplyAsync(
                    () -> calculateSolutionDiscount(availableCouponMap,orderCourses,solution),
                    discountSolutionExecutor)
                    .thenAccept(dto -> {
                        list.add(dto);
                        latch.countDown();
                    });
        }
        // 4.2 等待运算结束
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("计算优惠券方案失败", e);
        }
        // 5. 筛选出最优方案
        return findBestSolution(list);
//        return list;
    }

    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> list) {
        // 1. 准备map记录最优解决方案
        // 最大的优惠 和 最少用的券 取交集
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>(); // key ids value 优惠券
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();  // key 优惠折扣金额 value 优惠券

        // 2. 循环
        for (CouponDiscountDTO solution : list){
            // 2.1 计算当前方案的id组合
            String ids = solution.getIds()
                    .stream().sorted(Long::compare) // 按 id 升序排序 → [2, 5, 9]
                    .map(String::valueOf)// 把每个 Long 转成字符串 →["2", "5", "9"]
                    .collect(Collectors.joining(",")); // 用逗号拼成一个字符串"2,5,9"

            // 2.2 比较用券相同优惠金额是否最大
            CouponDiscountDTO best = moreDiscountMap.get(ids);
            if (best != null && best.getDiscountAmount() >= solution.getDiscountAmount()){
                // 当前方案优惠金额少,跳过
                continue;
            }
            // 2.3 比较金额相同,用券是否最少
            best = lessCouponMap.get(solution.getDiscountAmount());
            if (best != null && best.getIds().size() <= solution.getIds().size()){
                // 当前方案用券更多,跳过
                continue;
            }
            // 2.4 更新最优解决
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }

        // 3. 求交集
        Collection<CouponDiscountDTO> bestSolutions =
                CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());

        // 4. 排序,按优惠金额排序
        return bestSolutions.stream()
                .sorted(
                        Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount)
                        //告诉排序规则:拿每个对象的discountAmount 来比",默认从小到大
                        .reversed())
                .collect(Collectors.toList());
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
        // 添加折扣明细
        dto.setDiscountDetails(detailMap);
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


    // 查询优惠券方案计算订单优惠明细
    @Override
    public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO dto) {
        // 1.查询用户优惠券 防止用已经使用的券
        List<Long> userCouponIds = dto.getUserCouponIds();
        List<Coupon> coupons = userCouponMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.UNUSED);
        if (CollUtils.isEmpty(coupons)) return null;

        // 2.查询优惠券对应课程
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCourse(coupons, dto.getCourseList());
        if (CollUtils.isEmpty(availableCouponMap)) return null;

        // 3.查询优惠券规则
        return calculateSolutionDiscount(availableCouponMap, dto.getCourseList(), coupons);
    }



}
