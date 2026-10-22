package com.citylife.config;

import com.citylife.execute.RedisRateLimitHandler;
import com.citylife.lua.SlidingRateLimitOperate;
import com.citylife.lua.TokenBucketRateLimitOperate;
import com.citylife.ratelimit.extension.NoOpRateLimitEventListener;
import com.citylife.ratelimit.extension.NoOpRateLimitPenaltyPolicy;
import com.citylife.ratelimit.extension.RateLimitEventListener;
import com.citylife.ratelimit.extension.RateLimitPenaltyPolicy;
import com.citylife.ratelimit.extension.ThresholdPenaltyPolicy;
import com.citylife.redis.RedisCache;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @description: 布隆过滤器 配置
 **/
@EnableConfigurationProperties(SeckillRateLimitConfigProperties.class)
public class RateLimitAutoConfiguration {
    
    @Bean
    public SlidingRateLimitOperate slidingRateLimitOperate(RedisCache redisCache){
        return new SlidingRateLimitOperate(redisCache);
    }
    
    @Bean
    public TokenBucketRateLimitOperate tokenBucketRateLimitOperate(RedisCache redisCache){
        return new TokenBucketRateLimitOperate(redisCache);
    }

    @Bean
    public RateLimitEventListener rateLimitEventListener(){
        return new NoOpRateLimitEventListener();
    }

    @Bean
    public RateLimitPenaltyPolicy rateLimitPenaltyPolicy(SeckillRateLimitConfigProperties seckillRateLimitConfigProperties,
                                                         RedisCache redisCache){
        
        Boolean enable = seckillRateLimitConfigProperties.getEnablePenalty();
        if (Boolean.TRUE.equals(enable)) {
            return new ThresholdPenaltyPolicy(redisCache, seckillRateLimitConfigProperties);
        }
        return new NoOpRateLimitPenaltyPolicy();
    }

    @Bean
    public RedisRateLimitHandler redisRateLimitHandler(SeckillRateLimitConfigProperties seckillRateLimitConfigProperties,
                                                       RedisCache redisCache,
                                                       SlidingRateLimitOperate slidingRateLimitOperate,
                                                       TokenBucketRateLimitOperate tokenBucketRateLimitOperate,
                                                       RateLimitEventListener rateLimitEventListener,
                                                       RateLimitPenaltyPolicy rateLimitPenaltyPolicy) {
        return new RedisRateLimitHandler(
                seckillRateLimitConfigProperties, 
                redisCache,
                slidingRateLimitOperate,
                tokenBucketRateLimitOperate,
                rateLimitEventListener,
                rateLimitPenaltyPolicy
        );
    }
}
