# ratelimit-service

Reusable, out-of-process rate-limiting decision service - see roadmap Step 5.

## Why this exists alongside `shared/arceus-ratelimit`

`shared/arceus-ratelimit` is an in-process `@RateLimited` annotation +
`RedisRateLimiter` for services that already hold their own Redis
connection - it's a fixed-window counter, cheap, zero network hop, and
that's exactly what auth-service uses it for today (see its
`SecurityConfig`/controllers).

This service is different on purpose: it's the thing a **future API
Gateway** (or any non-Spring caller) asks over HTTP, and it implements the
two algorithms the roadmap actually asks for here - Sliding Window and Token
Bucket - neither of which the shared library does. Don't collapse the two;
they solve different problems (in-process cheap-and-approximate vs.
out-of-process accurate-and-shared).

## API

```
POST /check
```

Sliding window (exact - "at most N calls in any trailing window"):

```json
{
  "key": "login:ip:203.0.113.7",
  "algorithm": "SLIDING_WINDOW",
  "limit": 5,
  "windowSeconds": 60
}
```

Token bucket (allows bursts up to capacity, then a steady trickle):

```json
{
  "key": "api:user:42",
  "algorithm": "TOKEN_BUCKET",
  "capacity": 20,
  "refillPerSecond": 2
}
```

(`algorithm` defaults to `SLIDING_WINDOW` if omitted. Token bucket falls
back to `limit` for capacity if `capacity` isn't set, so simple callers can
use the same field name either way.)

Response (matches the roadmap spec exactly):

```json
{
  "allowed": true,
  "remainingRequests": 42,
  "retryAfter": 0
}
```

## How the algorithms work

- **Sliding window** (`SlidingWindowLimiter`) - one Redis sorted set per key,
  members are unique-per-call tokens scored by timestamp. Each check trims
  anything older than the window, counts what's left, and (if under the
  limit) adds itself - all in one Lua script so concurrent callers for the
  same key can't race past the limit.
- **Token bucket** (`TokenBucketLimiter`) - one Redis hash per key storing
  `tokens` + `lastRefillMillis`. Each check computes how many tokens should
  have regenerated since the last call (`elapsed * refillPerSecond`, capped
  at `capacity`), then tries to spend one. Also one Lua script, same
  atomicity reason.

Storage is Redis only, per the roadmap - no database here.

## Running it

Needs: Redis (same instance as the other services - see the root `.env`).

```powershell
.\mvnw.cmd -pl services/ratelimit-service spring-boot:run
```

Swagger UI at `http://localhost:8084/swagger-ui.html`.

## Known limitations worth knowing about

- **Callers must supply their own `key`.** This service has no idea what a
  "user" or "endpoint" is - namespacing (`login:ip:...`, `api:user:...`) is
  entirely the caller's responsibility. That's deliberate (see roadmap's
  future usage diagram: API Gateway -> this service -> everything else),
  but means there's no protection against two callers accidentally
  colliding on the same key today.
- **No admin/introspection endpoint** to see a key's current state without
  consuming from it - useful for debugging, not built yet.
- **Not wired into any other service yet.** auth-service still relies on
  `shared/arceus-ratelimit` for its own endpoints; nothing currently calls
  `POST /check` in anger. That's expected to change once the API Gateway
  exists.
