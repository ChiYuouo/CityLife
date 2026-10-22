package com.citylife.config;

import com.citylife.constant.LockInfoType;
import com.citylife.handle.RedissonDataHandle;
import com.citylife.locallock.LocalLockCache;
import com.citylife.lockinfo.LockInfoHandle;
import com.citylife.lockinfo.factory.LockInfoHandleFactory;
import com.citylife.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.citylife.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import com.citylife.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Bean;

/**
 * @description: 配置
 **/
public class RepeatExecuteLimitAutoConfiguration {
    
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle(){
        return new RepeatExecuteLimitLockInfoHandle();
    }
    
    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle){
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory,serviceLockFactory,redissonDataHandle);
    }
}
    