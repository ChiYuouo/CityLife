package com.citylife.config;

import com.citylife.constant.LockInfoType;
import com.citylife.core.ManageLocker;
import com.citylife.lockinfo.LockInfoHandle;
import com.citylife.lockinfo.factory.LockInfoHandleFactory;
import com.citylife.lockinfo.impl.ServiceLockInfoHandle;
import com.citylife.servicelock.aspect.ServiceLockAspect;
import com.citylife.servicelock.factory.ServiceLockFactory;
import com.citylife.util.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

/**
 * @description: 配置
 **/
public class ServiceLockAutoConfiguration {
    
    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle(){
        return new ServiceLockInfoHandle();
    }
    
    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient){
        return new ManageLocker(redissonClient);
    }
    
    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker){
        return new ServiceLockFactory(manageLocker);
    }
    
    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockAspect(lockInfoHandleFactory,serviceLockFactory);
    }
    
    @Bean
    public ServiceLockTool serviceLockTooL(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockTool(lockInfoHandleFactory,serviceLockFactory);
    }
}
