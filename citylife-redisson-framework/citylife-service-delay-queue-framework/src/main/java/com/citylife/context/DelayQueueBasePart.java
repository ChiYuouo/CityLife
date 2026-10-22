package com.citylife.context;

import com.citylife.config.DelayQueueProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.redisson.api.RedissonClient;

/**
 * @description: 延迟队列配置信息
 **/
@Data
@AllArgsConstructor
public class DelayQueueBasePart {
    
    private final RedissonClient redissonClient;
    
    private final DelayQueueProperties delayQueueProperties;
}
