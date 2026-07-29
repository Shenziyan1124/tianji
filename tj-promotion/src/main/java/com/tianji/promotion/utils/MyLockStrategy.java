package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import org.redisson.api.RLock;

public enum MyLockStrategy {
    SKIP_FAST {
        // 快速请求,不重试,直接结束
        @Override
        public Boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(0, prop.leaseTime(), prop.timeUnit());
        }
    },
    FAIL_FAST {
        // 快速请求,不重试,抛出异常
        @Override
        public Boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(0, prop.leaseTime(), prop.timeUnit());
            if (!isLock) throw new BizIllegalException("请求太频繁");

            return true;
        }
    },
    KEEP_TRYING {
        // 无限重试
        @Override
        public Boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            lock.tryLock(prop.leaseTime(), prop.timeUnit());
            return true;
        }
    },
    SKIP_AFTER_RETRY_TIMEOUT {
        // 有限等待时间重试,到时间直接结束
        @Override
        public Boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            return lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.timeUnit());
        }
    },
    FAIL_AFTER_RETRY_TIMEOUT {
        // 有限等待时间重试,到时间直接抛出异常
        @Override
        public Boolean tryLock(RLock lock, MyLock prop) throws InterruptedException {
            boolean isLock = lock.tryLock(prop.waitTime(), prop.leaseTime(), prop.timeUnit());
            if (!isLock) throw new BizIllegalException("请求太频繁");
            return true;
        }
    },
    ;

    public abstract Boolean tryLock(RLock lock,MyLock prop) throws InterruptedException;
}
