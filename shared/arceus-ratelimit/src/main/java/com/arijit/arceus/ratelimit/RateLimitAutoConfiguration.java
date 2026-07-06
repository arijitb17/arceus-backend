package com.arijit.arceus.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Any service that adds arceus-ratelimit as a dependency gets a working
 * RateLimiter + the @RateLimited aspect for free, as long as it also has a
 * StringRedisTemplate on the classpath (which auth-service and
 * notification-service already do, for OTP storage) - no manual @Bean wiring
 * needed in the consuming service.
 */
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
