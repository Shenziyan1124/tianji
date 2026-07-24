package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CouponCodeQuery;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_SERIAL_PREFIX;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author SHEN
 * @since 2026-07-23
 */
@Service
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {


    private final BoundValueOperations<String, String> ops;

    public ExchangeCodeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.ops = stringRedisTemplate.boundValueOps(COUPON_CODE_SERIAL_PREFIX);
    }

    // 异步生成兑换码
    @Override
    @Async("generateCodeExecutor")
    @Transactional
    public void asyncGenerateCodes(Coupon coupon) {
        // 最大的序列号
        Integer totalNum = coupon.getTotalNum();

        // 1. 获取redis自增序列号
        Long result = ops.increment(totalNum);
        if (result == null) return;
        int maxSerialNum = result.intValue();

        ArrayList<ExchangeCode> list = new ArrayList<>(totalNum);
        for (int serialNum = maxSerialNum - totalNum + 1; serialNum <= maxSerialNum; serialNum++){

            // 2. 生成兑换码
            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            // 3. 创建兑换码对象
            ExchangeCode e = new ExchangeCode();
            e.setCode(code);
            e.setId(serialNum);
            e.setExchangeTargetId(coupon.getId());
            e.setExpiredTime(coupon.getIssueEndTime());
            list.add(e);
        }
        saveBatch(list);
    }

    @Override
    public PageDTO<?> getCouponCodePage(CouponCodeQuery query) {
        Page<ExchangeCode> page = lambdaQuery()
                .eq(query.getCouponId() != null, ExchangeCode::getExchangeTargetId, query.getCouponId())
                .eq(query.getStatus() != null, ExchangeCode::getStatus, query.getStatus())
                .page(query.toMpPage());
        List<ExchangeCode> records = page.getRecords();
        if (CollUtils.isEmpty(records)) return PageDTO.empty(page);

        return PageDTO.of(page, records);
    }


}
