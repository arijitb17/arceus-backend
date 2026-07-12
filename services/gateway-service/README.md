# gateway-service

Single REST entry point for the Arceus Platform - see roadmap Phase 2. This
is the only service clients are meant to talk to directly; everything else
(auth, notification, audit, ratelimit) sits behind it.

## What it does

- Terminates REST from clients, forwards to every downstream service over
  **gRPC** (`shared/arceus-grpc-contracts`), never HTTP-to-HTTP.
- Validates the JWT access token auth-service issued, for every route
  except the public ones (register/login/refresh/OTP verification/health).
- Calls ratelimit-service before letting a protected request through,
  returning `429` with `Retry-After` when the caller is over budget.
- Assigns/propagates a request/correlation id (`X-Request-Id`) on every
  request, carried through to the downstream gRPC call so a single id can
  be traced across the whole hop.
- Logs one structured line per request: request id, user, method, path,
  status, duration.
- Turns every rejection - JWT failure, rate limit, 404, 405, unhandled
  exception, downstream unreachable - into the same JSON error shape.
- Exposes `/health`, which pings all four downstream services over gRPC
  and reports `UP` / `DEGRADED`.

It deliberately does **not** do load balancing, service discovery, circuit
breaking, caching, or anything Kubernetes-related - see the roadmap for why
those are out of scope for v1. It also holds no business logic and no
state of its own: every decision either comes from a downstream service's
response or from config.

## Request pipeline

Every request passes through, in this order (`FilterConfig`):

1. `RequestIdFilter` - assigns/reads `X-Request-Id`
2. `RequestLoggingFilter` - times the whole request, logs once at the end
3. `JwtAuthFilter` - rejects protected routes without a valid access token
4. `RateLimitFilter` - asks ratelimit-service before forwarding

...and only then does `ProxyController` translate the request into a
gRPC call and replay the response back to the client.

## Routing

| Client path            | Downstream service    | Downstream path   |
|-------------------------|-----------------------|--------------------|
| `/api/auth/**`          | auth-service           | `/api/auth/**`     |
| `/api/notifications/**` | notification-service   | `/notifications/**`|
| `/api/audit/**`         | audit-service          | `/audit-logs/**`   |

The Gateway never re-implements a service's request/response shapes: it
packs the raw method/path/headers/body into a generic `HttpRequestMessage`
(`arceus-grpc-contracts`), sends it over gRPC, and each service's own
`*GatewayGrpcService` forwards it to its existing REST controllers over a
loopback HTTP call. That controller stays the single source of truth for
that service's behavior - the Gateway is a thin, generic proxy, not a
second copy of the API.

`ratelimit-service` is the one exception: the Gateway calls it via a
dedicated typed RPC (`RateLimitCheck`) instead of the generic envelope,
since it's the one call made on every protected request.

## Public routes

No token required for:

```
/api/auth/register
/api/auth/verify-email
/api/auth/verify-phone
/api/auth/login
/api/auth/verify-login-otp
/api/auth/forgot-password
/api/auth/verify-forgot-password-otp
/api/auth/refresh
/health
```

Everything else needs `Authorization: Bearer <access-token>`. This list
lives in `arceus.gateway.public-paths` (`application.yml`) - it's a fast
first line of defense, not a replacement for each service's own security
config, which enforces the same rules independently.

## Configuration

Nothing is hardcoded - every downstream address, the JWT secret, and the
gateway's own coarse rate limit come from `application.yml` / the root
`.env`:

```yaml
spring:
  grpc:
    client:
      channels:
        auth:         { address: static://${AUTH_GRPC_HOST}:${AUTH_GRPC_PORT} }
        notification: { address: static://${NOTIFICATION_GRPC_HOST}:${NOTIFICATION_GRPC_PORT} }
        audit:        { address: static://${AUDIT_GRPC_HOST}:${AUDIT_GRPC_PORT} }
        ratelimit:    { address: static://${RATELIMIT_GRPC_HOST}:${RATELIMIT_GRPC_PORT} }

arceus:
  gateway:
    jwt:
      secret: ${JWT_SECRET}          # same secret auth-service signs with
    rate-limit:
      limit: ${GATEWAY_RATE_LIMIT}
      window-seconds: ${GATEWAY_RATE_LIMIT_WINDOW_SECONDS}
```

See the root `.env.example` for the full list of variables (`GATEWAY_PORT`,
`*_GRPC_HOST`, `*_GRPC_PORT`, etc).

## Running it

Needs auth-service, notification-service, audit-service, and
ratelimit-service reachable over gRPC on the ports above (they don't have
to be up for the Gateway itself to start - the client channels connect
lazily - but requests will fail/`/health` will report `DOWN` until they
are).

```bash
./mvnw -pl services/gateway-service spring-boot:run
```

Or start everything together with `scripts/run-all.sh` / `run-all.cmd`.
Default port: `8080`.

## Known limitations worth knowing about

- **Rate limiting fails open.** If ratelimit-service itself is unreachable,
  the Gateway logs a warning and lets the request through rather than
  taking the whole platform down over one dependency hiccup. Each
  service's own `@RateLimited` rules are still a backstop in that case.
- **No CORS configuration.** Not needed yet since nothing browser-based
  talks to the Gateway directly; add it if/when that changes.
- **Coarse rate limit only.** The Gateway's own limit is one number per
  identity across every protected route - it's a blunt edge-level backstop
  on top of, not instead of, each service's own fine-grained per-endpoint
  limits.
