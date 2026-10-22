package com.citylife.lockinfo.impl;

import com.citylife.lockinfo.AbstractLockInfoHandle;

/**
 * @description: 锁信息
 **/
public class RepeatExecuteLimitLockInfoHandle extends AbstractLockInfoHandle {

    public static final String PREFIX_NAME = "REPEAT_EXECUTE_LIMIT";
    
    @Override
    protected String getLockPrefixName() {
        return PREFIX_NAME;
    }
}
