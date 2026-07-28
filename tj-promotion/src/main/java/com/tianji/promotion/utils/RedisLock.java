package com.tianji.promotion.utils;

import com.tianji.common.utils.BooleanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RedisLock {

    private final String key;
    private final StringRedisTemplate redisTemplate;
    public Boolean tryLock(long leaseTime, TimeUnit unit){
        // 1. 获取线程
        String value = Thread.currentThread().getName();

        // 2. 尝试获取锁
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, leaseTime, unit);

        // 3. 返回结果
        return BooleanUtils.isTrue(success);
    }


    public void unlock(){
        // 1. 获取线程
        String value = Thread.currentThread().getName();

        // 2. 获取锁中的值
        String currentValue = redisTemplate.opsForValue().get(key);

        // 3. 判断锁中的值是否一致
        if (value.equals(currentValue)){
            // 4. 释放锁
            redisTemplate.delete(key);
        }
    }
}
