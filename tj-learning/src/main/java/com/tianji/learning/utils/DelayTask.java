package com.tianji.learning.utils;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayTask<D> implements Delayed {

    @Getter
    private D data; // 数据
    private final long deadlineNanos;  // 过期时间，单位纳秒

    public DelayTask(Duration delayTime, D data) {
        this.deadlineNanos = System.nanoTime() + delayTime.toNanos(); // 计算过期时间, 当前时间+延迟时间=过期时间
        this.data = data;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(Math.max(0,deadlineNanos - System.nanoTime()),TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(getDelay(TimeUnit.NANOSECONDS), o.getDelay(TimeUnit.NANOSECONDS));
    }

}
