package com.citylife.cache;

import jakarta.annotation.Resource;
import com.citylife.core.RedisKeyManage;
import com.citylife.kafka.message.SeckillVoucherInvalidationMessage;
import com.citylife.kafka.producer.SeckillVoucherInvalidationProducer;
import com.citylife.redis.RedisCache;
import com.citylife.redis.RedisKeyBuild;
import com.citylife.core.SpringUtil;
import org.springframework.stereotype.Component;

import static com.citylife.constant.Constant.SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC;

/**
 * @description: 业务发布入口：触发秒杀券缓存失效广播
 **/
@Component
public class SeckillVoucherCacheInvalidationPublisher {

    @Resource
    private RedisCache redisCache;
    
    @Resource
    private SeckillVoucherInvalidationProducer invalidationProducer;
    
    @Resource
    private SeckillVoucherLocalCache seckillVoucherLocalCache;
    
    public void publishInvalidate(Long voucherId, String reason) {
        RedisKeyBuild seckillVoucherRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId);
        seckillVoucherLocalCache.invalidate(seckillVoucherRedisKey.getRelKey());
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, voucherId));
        
        SeckillVoucherInvalidationMessage payload = new SeckillVoucherInvalidationMessage(voucherId, reason);
        invalidationProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC,
                payload
        );
    }
}