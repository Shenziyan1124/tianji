package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    // 签到
    @Override
    public SignResultVO addSignRecord() {
        // 1.签到
        // 1.1获取登录信息
        Long userId = UserContext.getUser();
        // 1.2获取当前日期
        LocalDate now = LocalDate.now();
        // 1.3拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 1.4计算offset
        int offset = now.getDayOfMonth() - 1;
        // 1.5保存记录
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        if (Boolean.TRUE.equals(exits)) {
            throw new BizIllegalException("今日已签到");
        }
        // 2.计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());
        // 3.计算签到积分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        //  4.保存积分明细
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1)
        );
        // 5.封装返回vo
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    private int countSignDays(String key, int len) {
        // 1.获取本月到现在的所有签到记录
        List<Long> list = redisTemplate.opsForValue().
                bitField(
                        key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0)
                );
        if (CollUtils.isEmpty(list)) {
            return 0;
        }
        int num = list.get(0).intValue();
        // 2.定义计数器
        int count = 0;
        // 3.循环,与1进行运算,得到最后一个bit,是否为0, 0终止
        while ((num & 1) == 1) {
            // 4.计数器+1
            count++;
            // 5.把最后一个数字右移,最后一位被舍弃,倒数第二位成了倒数第一位
            num >>= 1;
        }
        return count;
    }


    // 获取签到记录
    public List<Integer> getSignRecord() {
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();

        // 1.拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 2.获取本月天数
        int days = now.lengthOfMonth();
        // 3.获取本月所有签到记录
        List<Long> list = redisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(days)).valueAt(0)
        );
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        int num = list.get(0).intValue();
        // 4.将二进制数据转换为List<Integer>
        List<Integer> result = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            // 从高位到低位依次获取每一位，使下标0对应1号
            result.add((num >> i) & 1);
        }
        return result;
    }
}
