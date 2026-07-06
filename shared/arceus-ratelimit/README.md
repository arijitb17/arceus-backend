# arceus-ratelimit

A `@RateLimited` annotation + Redis-backed limiter, shared by any service
that needs to cap how often something can be called - no separate
rate-limit service, no extra network hop on the request path.

## Why a shared library instead of a `rate-limit-service`

Rate limiting isn't a domain the way authentication or notification are -
there's no meaningful business logic that benefits from having its own
service boundary, only a shared primitive (an atomic counter against
Redis) that any service can use directly. A dedicated service would mean
every rate-limited call becomes a network round-trip before the real work
even starts - the opposite of what you want on a hot path like login.
Redis is already shared infrastructure every service in this platform has
a legitimate direct connection to (see `arceus-events`'s README for the same
argument applied to OTP state), so this follows the same pattern instead of
inventing a new one.

## How it works

`RedisRateLimiter` runs one Lua script per check: `INCR` a key, and if this
was the first increment, `EXPIRE` it to the window length - both in one
atomic Redis operation, so two concurrent requests can't both think they're
"first." This is a **fixed window** counter, not a sliding one: a client can
burst up to ~2x the limit right at a window boundary (e.g. near the max
right before a window resets, then near the max again right after). That's
an accepted tradeoff for the simplicity of one `INCR` instead of a
`ZSET`-based sliding log - swap `RedisRateLimiter` for a sliding-window
implementation later if a specific bucket ever needs stricter guarantees;
nothing else in this module needs to change, since everything goes through
the `RateLimiter` interface.

## Using it

Add the dependency:

```xml
<dependency>
    <groupId>com.arijit</groupId>
    <artifactId>arceus-ratelimit</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

If the service already has a `StringRedisTemplate` on its classpath (true
for any service already storing OTP/session state in Redis), the
`RateLimiter` bean and the enforcing aspect are wired up automatically via
`RateLimitAutoConfiguration` - no manual `@Bean` needed.

Then annotate a method:

```java
@RateLimited(bucket = "login-ip", perIp = true, limit = 20, windowSeconds = 900)
@RateLimited(bucket = "login-user", key = "#request.email()", limit = 5, windowSeconds = 900)
@PostMapping("/login")
public ResponseEntity<...> login(@Valid @RequestBody LoginRequest request) { ... }
```

- `bucket` namespaces the Redis key so different rules (even on the same
  method) never collide with each other.
- `perIp = true` folds the caller's resolved IP into the key - stops one
  client hammering the endpoint, regardless of which account it targets.
- `key` is a SpEL expression evaluated against the method's parameters
  (same style as `@PreAuthorize`/`@Cacheable`) - use it to limit per
  identifier (email, phone, user id) instead of / as well as per IP. Stops
  someone spraying requests at one victim from many different IPs.
- The annotation is `@Repeatable` - stack both on one method, as above,
  when you want both protections. Whichever rule trips first blocks the
  call.

When a limit is hit, `RateLimitAspect` throws `RateLimitExceededException`
(carrying a `retryAfterSeconds`) instead of proceeding. This module doesn't
map that to an HTTP status itself - each consuming service handles it in
its own exception handler, e.g. auth-service's `GlobalExceptionHandler`:

```java
@ExceptionHandler(RateLimitExceededException.class)
public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(errorBody(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
}
```

## Known limitations worth knowing about

- **Fixed window, not sliding** - see above. Acceptable for login/OTP abuse
  protection; revisit per-bucket if a specific rule needs a hard cap with
  no boundary burst.
- **`X-Forwarded-For` is trusted as-is** for `perIp` resolution
  (`ClientIpResolver`). That's only safe once a reverse proxy or
  gateway-service sits in front of every service and sets that header
  itself, overwriting anything a client sent. Right now, with services
  reachable directly, a client can spoof this header to dodge `perIp`
  limiting by claiming a different IP each request. Revisit the moment
  gateway-service exists and becomes the sole public entry point - at that
  point this class is correct as-is, it just needs the gateway to be the
  trustworthy source of that header.
- **No per-bucket configuration outside the annotation** - limits and
  windows are hardcoded at the call site, not externalized to
  `application.yml`. Fine for a project this size; if limits need to be
  tunable per-environment later, that's a natural follow-up (e.g. resolve
  `limit`/`windowSeconds` from a `${...}` property instead of a literal).

## Depending on this module

Run `.\mvnw.cmd install -DskipTests` from the project root at least once so
this jar exists in your local Maven repo before a service that depends on
it can resolve it (or use `-pl services/xxx -am` to build it as part of the
same reactor run).