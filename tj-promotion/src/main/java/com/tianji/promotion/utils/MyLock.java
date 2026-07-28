package com.tianji.promotion.utils;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME) // 表示注解保留在运行时
@Target(ElementType.METHOD) // 表示注解作用在方法上
public @interface MyLock {
    String name();

    // 锁等待时间
    long waitTime() default 1;

    // 锁超时时间 -1可以触发看门狗
    long leaseTime() default -1;

    // 时间单位
    TimeUnit timeUnit() default TimeUnit.SECONDS;


    MyLockType lockType() default MyLockType.RE_ENTRANT_LOCK;
}
