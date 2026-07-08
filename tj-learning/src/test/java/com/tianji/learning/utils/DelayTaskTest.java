package com.tianji.learning.utils;

import groovy.util.logging.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;

import static org.junit.jupiter.api.Assertions.*;

@lombok.extern.slf4j.Slf4j
@Slf4j
class DelayTaskTest {
    @Test
    void testDelayTask() throws InterruptedException {
        DelayQueue<DelayTask<String>> delayQueue = new DelayQueue<>();

        log.info("开始添加延迟任务");
        delayQueue.add(new DelayTask<>(Duration.ofSeconds(5), "任务3"));
        delayQueue.add(new DelayTask<>(Duration.ofSeconds(1), "任务1"));
        delayQueue.add(new DelayTask<>(Duration.ofSeconds(2), "任务2"));


        while (true){
            DelayTask<String> take = delayQueue.take();
            log.info("获取到任务：{}", take.getData());
        }
    }
}