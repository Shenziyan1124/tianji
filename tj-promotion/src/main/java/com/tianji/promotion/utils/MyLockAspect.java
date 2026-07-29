package com.tianji.promotion.utils;


import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
@Aspect // 创建切面
@RequiredArgsConstructor // 创建构造方法
public class MyLockAspect implements Ordered {

    //private final RedissonClient redissonClient;
    private final MyLockFactory myLockFactory;

    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        // 1. 创建锁对象
        RLock lock = myLockFactory.getLock(myLock.lockType(), myLock.name());
//        switch (myLock.lockType()) {
//            case RE_ENTRANT_LOCK:
//                lock = redissonClient.getLock(myLock.name());
//                break;
//            case FAIR_LOCK:
//                lock = redissonClient.getFairLock(myLock.name());
//                break;
//            case READ_LOCK:
//                lock = redissonClient.getReadWriteLock(myLock.name()).readLock();
//                break;
//            case WRITE_LOCK:
//                lock = redissonClient.getReadWriteLock(myLock.name()).writeLock();
//            default:
//                throw new BizIllegalException("锁类型错误");
//        }
        // 2. 获取锁
        boolean isLock = myLock.lockStrategy().tryLock(lock,myLock);
        // 3. 判断是否成功
        if (!isLock) {
            // 4. 失败抛异常
            //throw new BizIllegalException("请求太频繁");
            // 在lockStrategy做了处理抛异常处理
            return null;
        }
        try {
            // 5. 成功,执行业务
            return pjp.proceed();
        } finally {
            // 6. 释放锁
            lock.unlock();
        }
    }

    // 7. 设置切面的优先级,是想先锁在事务,锁的优先级要高
    @Override
    public int getOrder() {
        return 0;
    }
}
