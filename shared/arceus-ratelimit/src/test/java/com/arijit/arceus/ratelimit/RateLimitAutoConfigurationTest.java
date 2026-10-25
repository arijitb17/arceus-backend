package com.arijit.arceus.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void registersRedisRateLimiterAndAspect() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RateLimiter.class);
            assertThat(ctx.getBean(RateLimiter.class)).isInstanceOf(RedisRateLimiter.class);
            assertThat(ctx).hasSingleBean(RateLimitAspect.class);
        });
    }

    @Test
    void backsOffWhenCustomRateLimiterPresent() {
        RateLimiter custom = (key, limit, window) -> new RateLimitResult(true, 0, 0);

        runner.withBean(RateLimiter.class, () -> custom).run(ctx -> {
            assertThat(ctx).hasSingleBean(RateLimiter.class);
            assertThat(ctx.getBean(RateLimiter.class)).isSameAs(custom);
            assertThat(ctx).hasSingleBean(RateLimitAspect.class);
        });
    }

    @Test
    void backsOffWhenCustomAspectPresent() {
        RateLimitAspect custom = new RateLimitAspect((k, l, w) -> new RateLimitResult(true, 0, 0));

        runner.withBean(RateLimitAspect.class, () -> custom).run(ctx ->
                assertThat(ctx.getBean(RateLimitAspect.class)).isSameAs(custom));
    }

    @Test
    void failsWithoutRedisTemplateBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void wiredAspectEnforcesLimitsEndToEnd() {
        runner.withBean(RateLimiter.class, () -> (key, limit, window) -> new RateLimitResult(false, limit + 1, 7))
                .withBean(Guarded.class, Guarded::new)
                .run(ctx -> {
                    Guarded guarded = ctx.getBean(Guarded.class);
                    org.assertj.core.api.Assertions.assertThatThrownBy(guarded::call)
                            .isInstanceOfSatisfying(RateLimitExceededException.class,
                                    ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(7));
                });
    }

    public static class Guarded {
        @RateLimited(bucket = "e2e", limit = 1, windowSeconds = 5)
        public String call() {
            return "reached";
        }
    }

}
