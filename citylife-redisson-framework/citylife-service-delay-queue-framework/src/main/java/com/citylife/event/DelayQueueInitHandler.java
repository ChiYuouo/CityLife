package com.citylife.event;

import cn.hutool.core.collection.CollectionUtil;
import com.citylife.context.DelayQueueBasePart;
import com.citylife.context.DelayQueuePart;
import com.citylife.core.ConsumerTask;
import com.citylife.core.DelayConsumerQueue;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.util.Map;

/**
 * @description: 事件
 **/
@AllArgsConstructor
public class DelayQueueInitHandler implements ApplicationListener<ApplicationStartedEvent> {
    
    private final DelayQueueBasePart delayQueueBasePart;
    
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {

        Map<String, ConsumerTask> consumerTaskMap = event.getApplicationContext().getBeansOfType(ConsumerTask.class);
        if (CollectionUtil.isEmpty(consumerTaskMap)) {
            return;
        }
        for (ConsumerTask consumerTask : consumerTaskMap.values()) {
            DelayQueuePart delayQueuePart = new DelayQueuePart(delayQueueBasePart,consumerTask);
            Integer isolationRegionCount = delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties()
                    .getIsolationRegionCount();
            
            for(int i = 0; i < isolationRegionCount; i++) {
                DelayConsumerQueue delayConsumerQueue = new DelayConsumerQueue(delayQueuePart, 
                        delayQueuePart.getConsumerTask().topic() + "-" + i);
                delayConsumerQueue.listenStart();
            }
        }
    }
}
