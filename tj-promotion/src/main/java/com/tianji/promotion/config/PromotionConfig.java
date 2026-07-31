package com.tianji.promotion.config;

import com.tianji.common.autoconfigure.xxljob.XxlJobProperties;
import com.tianji.promotion.utils.MyLockAspect;
import com.tianji.promotion.utils.MyLockFactory;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Slf4j
public class PromotionConfig {
    @Bean
    public Executor generateCodeExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(2);
        // 最大线程数
        executor.setMaxPoolSize(5);
        // 队列大小
        executor.setQueueCapacity(100);
        // 线程名称前缀
        executor.setThreadNamePrefix("exchange-code-handler-");
        // 拒绝策略，当线程数达到最大线程数时，新的任务会使用拒绝策略进行处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public Executor discountSolutionExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(4);
        // 最大线程数
        executor.setMaxPoolSize(8);
        // 队列大小
        executor.setQueueCapacity(10000);
        // 线程名称前缀
        executor.setThreadNamePrefix("discount-solution-calculator-");
        // 拒绝策略，当线程数达到最大线程数时，新的任务会使用拒绝策略进行处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

//    @Bean
//    public MyLockAspect myLockAspect(RedissonClient redissonClient){
//        return new MyLockAspect(redissonClient);
//    }
}
