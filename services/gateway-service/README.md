# gateway-service

The single entry point for every client (portal, SDKs, third-party apps). It holds no business
logic and no state: it authenticates, protects and routes.

Port **8090**.

## What it does

- Terminates REST/JSON and forwards each request to the owning service over **gRPC**, using a
  generic `HttpRequestMessage` envelope from `arceus-grpc-contracts`. Each service's own
  `*GatewayGrpcService` replays it into its existing REST controllers, so the controller stays the
  single source of truth.
- Authenticates with either a **JWT** (`Authorization: Bearer ...`) or a **project API key**
  (`X-API-Key`). An invalid API key fails outright instead of falling back to JWT.
- Applies a per-route **feature-flag kill switch** (`RouteFlagFilter`).
- Applies a coarse **rate limit** per identity via `ratelimit-service`, returning `429` with
  `Retry-After`.
- Assigns/propagates `X-Request-Id` and logs one structured line per request.
- Handles **CORS** (`GATEWAY_CORS_ALLOWED_ORIGINS`).
- Publishes **usage events** per project request, consumed by monitoring-service.
- Streams platform events to admins over **SSE** at `/api/live/events`.
- Returns one JSON error shape for every rejection.
- Exposes `/health`, which checks the downstream services over gRPC and reports `UP` / `DEGRADED`.

## Filter order

`CorsFilter` → `RequestIdFilter` → `RequestLoggingFilter` → `ApiKeyAuthFilter` → `JwtAuthFilter` →
`RouteFlagFilter` → `RateLimitFilter` → `ProxyController`

## Routing

| Client path | Service | Downstream path |
|---|---|---|
| `/api/auth/**` | auth-service | `/api/auth/**` |
| `/api/organizations/**` | organization-service | `/organizations/**` |
| `/api/notifications/**` | notification-service | `/notifications/**` |
| `/api/cache/**` | cache-service | `/cache/**` |
| `/api/scheduler/**` | scheduler-service | `/jobs/**` |
| `/api/feature-flags/**` | feature-flag-service | `/flags/**` |
| `/api/monitoring/**` | monitoring-service | `/**` |

`ratelimit-service` is called through a dedicated typed RPC (`RateLimitCheck`), not proxied.

## Public routes (no token)

`register`, `verify-email`, `verify-phone`, `login`, `verify-login-otp`, `forgot-password`,
`verify-forgot-password-otp`, `reset-password`, `refresh` and `oauth2/exchange` under `/api/auth/`,
plus `/health` and `/actuator/health`. The list lives in `arceus.gateway.public-paths`
(`application.yml`).

## Configuration

| Variable | Purpose |
|---|---|
| `GATEWAY_PORT` | HTTP port (default 8090) |
| `JWT_SECRET` | Must match auth-service |
| `GATEWAY_RATE_LIMIT`, `GATEWAY_RATE_LIMIT_WINDOW_SECONDS` | Edge rate limit |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | Comma-separated browser origins |
| `GATEWAY_FEATURE_FLAG_ENVIRONMENT` | Environment used for route flags |
| `*_GRPC_HOST`, `*_GRPC_PORT` | Downstream gRPC addresses (auth, notification, ratelimit, cache, scheduler, feature-flag, monitoring, organization) |
| `*_INTERNAL_API_TOKEN` | Shared secrets for internal gRPC calls |

See the root `.env.example` for the full list.

## Run

```bash
./mvnw -q install -DskipTests
./mvnw -pl services/gateway-service spring-boot:run
```

Downstream services do not need to be up for the Gateway to start (channels connect lazily), but
proxied requests fail and `/health` reports them down until they are.

## Limitations

- **Rate limiting fails open** if ratelimit-service is unreachable; each service's own
  `@RateLimited` rules remain as a backstop.
- **One coarse limit per identity** across all protected routes.
- **No load balancing, service discovery or circuit breaking.**
