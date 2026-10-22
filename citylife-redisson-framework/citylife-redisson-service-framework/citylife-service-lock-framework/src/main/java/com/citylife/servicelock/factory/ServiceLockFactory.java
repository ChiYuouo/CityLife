package com.citylife.servicelock.factory;

import com.citylife.core.ManageLocker;
import com.citylife.servicelock.LockType;
import com.citylife.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;

/**
 * @description: 工厂
 **/
@AllArgsConstructor
public class ServiceLockFactory {
    
    private final ManageLocker manageLocker;
    

    public ServiceLocker getLock(LockType lockType){
        ServiceLocker lock;
        switch (lockType) {
            case Fair:
                lock = manageLocker.getFairLocker();
                break;
            case Write:
                lock = manageLocker.getWriteLocker();
                break;
            case Read:
                lock = manageLocker.getReadLocker();
                break;
            default:
                lock = manageLocker.getReentrantLocker();
                break;
        }
        return lock;
    }
}
