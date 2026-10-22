package com.citylife.core;

import com.citylife.servicelock.LockType;
import com.citylife.servicelock.ServiceLocker;
import com.citylife.servicelock.impl.RedissonFairLocker;
import com.citylife.servicelock.impl.RedissonReadLocker;
import com.citylife.servicelock.impl.RedissonReentrantLocker;
import com.citylife.servicelock.impl.RedissonWriteLocker;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.Map;

import static com.citylife.servicelock.LockType.Fair;
import static com.citylife.servicelock.LockType.Read;
import static com.citylife.servicelock.LockType.Reentrant;
import static com.citylife.servicelock.LockType.Write;

/**
 * @description: 缓存
 **/
public class ManageLocker {

    private final Map<LockType, ServiceLocker> cacheLocker = new HashMap<>();
    
    public ManageLocker(RedissonClient redissonClient){
        cacheLocker.put(Reentrant,new RedissonReentrantLocker(redissonClient));
        cacheLocker.put(Fair,new RedissonFairLocker(redissonClient));
        cacheLocker.put(Write,new RedissonWriteLocker(redissonClient));
        cacheLocker.put(Read,new RedissonReadLocker(redissonClient));
    }
    
    public ServiceLocker getReentrantLocker(){
        return cacheLocker.get(Reentrant);
    }
    
    public ServiceLocker getFairLocker(){
        return cacheLocker.get(Fair);
    }
    
    public ServiceLocker getWriteLocker(){
        return cacheLocker.get(Write);
    }
    
    public ServiceLocker getReadLocker(){
        return cacheLocker.get(Read);
    }
}
