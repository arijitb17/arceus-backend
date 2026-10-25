package com.arijit.arceus.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {
    private final RateLimiter rateLimiter;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.arijit.arceus.ratelimit.RateLimited) || @annotation(com.arijit.arceus.ratelimit.RateLimited.List)")
    public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();

        for (RateLimited rule : method.getAnnotationsByType(RateLimited.class)) {
            String key = resolveKey(rule, method, args);
            RateLimitResult result = rateLimiter.tryConsume(
                    rule.bucket() + ":" + key,
                    rule.limit(),
                    Duration.ofSeconds(rule.windowSeconds())
            );

            if (!result.allowed()) {
                log.warn("Rate limit exceeded: bucket={} method={} retryAfterSeconds={}",
                        rule.bucket(), method.getName(), result.retryAfterSeconds());
                throw new RateLimitExceededException(
                        "Too many requests for '%s' - try again in %ds".formatted(rule.bucket(), result.retryAfterSeconds()),
                        result.retryAfterSeconds()
                );
            }
        }

        return joinPoint.proceed();
    }

    private String resolveKey(RateLimited rule, Method method, Object[] args) {
        StringBuilder key = new StringBuilder();

        if (rule.perIp()) {
            key.append("ip:").append(ClientIpResolver.resolve());
        }

        if (!rule.key().isBlank()) {
            if (!key.isEmpty()) key.append(':');
            EvaluationContext context = new MethodBasedEvaluationContext(null, method, args, paramNameDiscoverer);
            Object value = parser.parseExpression(rule.key()).getValue(context);
            key.append(value);
        }

        return key.isEmpty() ? "global" : key.toString();
    }
}
