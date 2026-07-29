package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.tianji.promotion.utils.MyLockType.*;

@Component
public class MyLockFactory {


    private final Map<MyLockType, Function<String,RLock>> lockHandlers;

    public MyLockFactory(RedissonClient redissonClient) {
        this.lockHandlers = new EnumMap<>(MyLockType.class); // 创建一个枚举类型的Map
        lockHandlers.put(RE_ENTRANT_LOCK, redissonClient::getLock);
        lockHandlers.put(FAIR_LOCK, redissonClient::getFairLock);
        lockHandlers.put(READ_LOCK, name -> redissonClient.getReadWriteLock(name).readLock());
        lockHandlers.put(WRITE_LOCK, name -> redissonClient.getReadWriteLock(name).writeLock());
    }

    public RLock getLock(MyLockType lockType, String name){
        return lockHandlers.get(lockType).apply(name);
    }

}
