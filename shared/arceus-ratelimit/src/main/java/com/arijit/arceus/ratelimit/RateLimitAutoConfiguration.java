package com.arijit.arceus.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@EnableAspectJAutoProxy
public class RateLimitAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiter rateLimiter) {
        return new RateLimitAspect(rateLimiter);
    }
}
