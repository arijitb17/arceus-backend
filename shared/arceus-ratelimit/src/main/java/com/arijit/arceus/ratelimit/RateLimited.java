package com.arijit.arceus.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Put on any Spring-managed bean method (typically a @RestController handler)
 * to cap how often it can be called.
 *
 * Two independent dimensions, use either or both by stacking the annotation
 * (it's @Repeatable) - this matters because they catch different abuse
 * patterns:
 *
 *   - perIp = true: caps calls from a single IP, regardless of which
 *     account/email it's hitting. Stops one client hammering the endpoint.
 *   - key = "#request.email()": caps calls for one identifier (email,
 *     phone, userId...), regardless of source IP. Stops someone spraying
 *     OTP requests at a single victim from many different IPs/devices.
 *
 * Example - two rules on one endpoint:
 * <pre>{@code
 * @RateLimited(bucket = "login-ip", perIp = true, limit = 20, windowSeconds = 900)
 * @RateLimited(bucket = "login-user", key = "#request.email()", limit = 5, windowSeconds = 900)
 * @PostMapping("/login")
 * public ResponseEntity<...> login(@RequestBody LoginRequest request) { ... }
 * }</pre>
 *
 * {@code key} is a SpEL expression evaluated against the method's
 * parameters (accessible by name, same as {@code @PreAuthorize}/
 * {@code @Cacheable} key expressions) - e.g. {@code "#request.email()"}
 * for a record parameter named {@code request} with an {@code email()} accessor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(RateLimited.List.class)
public @interface RateLimited {

    /**
     * Logical name for this rule, e.g. "login", "otp-resend". Namespaces the
     * underlying Redis key so unrelated rules on different endpoints (or
     * multiple rules on the same endpoint) can never collide with each other.
     */
    String bucket();

    /** SpEL expression evaluated against method parameters. Leave blank if perIp() alone is enough. */
    String key() default "";

    /** Max calls allowed within the window. */
    int limit();

    /** Window length in seconds. */
    long windowSeconds();

    /** If true, the caller's resolved IP is folded into the key. */
    boolean perIp() default false;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        RateLimited[] value();
    }
}
