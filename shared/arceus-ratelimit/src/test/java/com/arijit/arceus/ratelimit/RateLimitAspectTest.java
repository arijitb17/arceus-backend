package com.arijit.arceus.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Integration-style tests: the real aspect is woven around a real annotated target through
 * Spring's AspectJProxyFactory, with only the {@link RateLimiter} mocked.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RateLimiter rateLimiter;

    private Target proxy;

    public static class Target {

        @RateLimited(bucket = "global-bucket", limit = 10, windowSeconds = 60)
        public String globalRule() {
            return "ok";
        }

        @RateLimited(bucket = "login", key = "#p0", limit = 5, windowSeconds = 30)
        public String byKey(String email) {
            return "hello " + email;
        }

        @RateLimited(bucket = "ip-only", perIp = true, limit = 3, windowSeconds = 15)
        public String byIp() {
            return "ip";
        }

        @RateLimited(bucket = "combo", perIp = true, key = "#p0", limit = 2, windowSeconds = 10)
        public String ipAndKey(String id) {
            return id;
        }

        @RateLimited(bucket = "short", key = "#p0", limit = 1, windowSeconds = 1)
        @RateLimited(bucket = "long", key = "#p0", limit = 100, windowSeconds = 3600)
        public String twoRules(String id) {
            return id;
        }

        public String unannotated() {
            return "free";
        }
    }

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.addAspect(new RateLimitAspect(rateLimiter));
        factory.setProxyTargetClass(true);
        proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static RateLimitResult allowed() {
        return new RateLimitResult(true, 1, 0);
    }

    private static RateLimitResult denied(long retry) {
        return new RateLimitResult(false, 99, retry);
    }

    @Nested
    class Allowed {

        @Test
        void proceedsAndUsesGlobalKeyWhenNoKeyOrIp() {
            when(rateLimiter.tryConsume("global-bucket:global", 10, Duration.ofSeconds(60))).thenReturn(allowed());

            assertThat(proxy.globalRule()).isEqualTo("ok");

            verify(rateLimiter).tryConsume("global-bucket:global", 10, Duration.ofSeconds(60));
        }

        @Test
        void evaluatesSpelKeyAgainstMethodArguments() {
            when(rateLimiter.tryConsume("login:a@b.com", 5, Duration.ofSeconds(30))).thenReturn(allowed());

            assertThat(proxy.byKey("a@b.com")).isEqualTo("hello a@b.com");

            verify(rateLimiter).tryConsume("login:a@b.com", 5, Duration.ofSeconds(30));
        }

        @Test
        void perIpUsesForwardedAddress() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "9.9.9.9, 1.1.1.1");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            when(rateLimiter.tryConsume("ip-only:ip:9.9.9.9", 3, Duration.ofSeconds(15))).thenReturn(allowed());

            assertThat(proxy.byIp()).isEqualTo("ip");

            verify(rateLimiter).tryConsume("ip-only:ip:9.9.9.9", 3, Duration.ofSeconds(15));
        }

        @Test
        void perIpWithoutRequestContextFallsBackToMarker() {
            when(rateLimiter.tryConsume("ip-only:ip:no-request-context", 3, Duration.ofSeconds(15))).thenReturn(allowed());

            assertThat(proxy.byIp()).isEqualTo("ip");
        }

        @Test
        void combinesIpAndKeyWithColon() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("5.5.5.5");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            when(rateLimiter.tryConsume("combo:ip:5.5.5.5:u-1", 2, Duration.ofSeconds(10))).thenReturn(allowed());

            assertThat(proxy.ipAndKey("u-1")).isEqualTo("u-1");

            verify(rateLimiter).tryConsume("combo:ip:5.5.5.5:u-1", 2, Duration.ofSeconds(10));
        }

        @Test
        void unannotatedMethodsAreNotIntercepted() {
            assertThat(proxy.unannotated()).isEqualTo("free");

            verifyNoInteractions(rateLimiter);
        }

        @Test
        void repeatedAnnotationsAreAllChecked() {
            when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class))).thenReturn(allowed());

            assertThat(proxy.twoRules("x")).isEqualTo("x");

            verify(rateLimiter).tryConsume("short:x", 1, Duration.ofSeconds(1));
            verify(rateLimiter).tryConsume("long:x", 100, Duration.ofSeconds(3600));
        }
    }

    @Nested
    class Rejected {

        @Test
        void throwsWithRetryAfterAndDoesNotInvokeTarget() {
            when(rateLimiter.tryConsume(eq("login:a@b.com"), anyInt(), any(Duration.class))).thenReturn(denied(25));

            assertThatThrownBy(() -> proxy.byKey("a@b.com"))
                    .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
                        assertThat(ex.getRetryAfterSeconds()).isEqualTo(25);
                        assertThat(ex.getMessage()).contains("login").contains("25");
                    });
        }

        @Test
        void firstFailingRuleShortCircuitsRemainingRules() {
            when(rateLimiter.tryConsume(eq("short:x"), anyInt(), any(Duration.class))).thenReturn(denied(1));

            assertThatThrownBy(() -> proxy.twoRules("x")).isInstanceOf(RateLimitExceededException.class);

            verify(rateLimiter, times(1)).tryConsume(anyString(), anyInt(), any(Duration.class));
            verify(rateLimiter, never()).tryConsume(eq("long:x"), anyInt(), any(Duration.class));
        }

        @Test
        void secondRuleCanAlsoReject() {
            when(rateLimiter.tryConsume(eq("short:x"), anyInt(), any(Duration.class))).thenReturn(allowed());
            when(rateLimiter.tryConsume(eq("long:x"), anyInt(), any(Duration.class))).thenReturn(denied(3000));

            assertThatThrownBy(() -> proxy.twoRules("x"))
                    .isInstanceOfSatisfying(RateLimitExceededException.class,
                            ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(3000));
        }
    }
}
