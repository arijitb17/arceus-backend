# Arceus Admin Portal

Enterprise admin console for the Arceus backend infrastructure platform.
Next.js 15 (App Router) + TypeScript + Tailwind + shadcn-style components +
TanStack Query + React Hook Form + Zod + Recharts.

**Status: architecture + Authentication (login) + Dashboard are implemented.**
Everything else in the sidebar is scaffolded as navigation but not yet built -
see the module-by-module plan below and `README_BACKEND_GAPS.md` for what's
blocked on the backend.

## Running it

```bash
npm install
cp .env.local.example .env.local   # only one URL needed now - see below
npm run dev
```

Requires `gateway-service` running at `http://localhost:8090` (default).
**Every service is now routed through it** - Auth, Notifications, Audit,
Monitoring, Deployments, Scheduler, Feature Flags, and Cache all go through
`lib/api/gateway-client.ts`. `ratelimit-service` stays internal-only by
design.

**Backend-side env you'll also need:** the Gateway's CORS allow-list is
empty by default (`GATEWAY_CORS_ALLOWED_ORIGINS`), so requests from
`http://localhost:3000` will be blocked until you set it on the backend:

```
GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:3000
```

## Architecture

```
app/                    Next.js App Router routes
  (auth)/login/          Public: split-panel login screen
  dashboard/              Protected: platform overview
components/
  ui/                     Hand-built shadcn-style primitives (button, card, dialog, ...)
  shared/                 Cross-module pieces: StatusBadge, StatCard, EmptyState, ErrorState,
                          DirectServiceNotice (the "not gatewayed yet" warning banner)
features/
  auth/                   Login form + validation schemas
  dashboard/              One widget component per dashboard card
  layout/                 Sidebar, Topbar, AppShell (the auth-gated shell)
hooks/                  TanStack Query hooks - one file per domain (use-auth, use-dashboard)
services/               Thin fetch wrappers, one file per backend service, matching its
                        real controllers 1:1 - nothing here calls an endpoint that doesn't exist
lib/
  api/                    gateway-client.ts (Bearer-JWT, auto-refresh on 401 - the only HTTP client now)
                          sse.ts (hand-rolled fetch/ReadableStream SSE parser for live updates)
                          error.ts (reads the unified backend error shape)
  config.ts               Single Gateway base URL, from env
  nav-config.ts           Single source of truth for the sidebar - marks each module
                          live / missing-backend
store/                  Zustand auth store (JWT pair + user, persisted to localStorage)
types/                  One file per domain, hand-transcribed from the actual backend DTOs
providers/              QueryClientProvider, TooltipProvider, Toaster composition
```

## Design system

Dark-first, tokens exactly as specified: background `#0B1220`, surface
`#111827`, card `#1F2937`, border `#374151`, primary `#2563EB`, accent
`#7C3AED`, success/warning/error `#10B981`/`#F59E0B`/`#EF4444`, text `#F9FAFB`.
All in `tailwind.config.ts`. Monospace treatment (`.font-data`) is used
throughout for ids, versions, and correlation ids - a deliberate nod to the
Grafana/GitHub reference points, and a way to keep the UI reading as
infrastructure tooling rather than a generic SaaS dashboard.

## Auth flow (matches the backend exactly)

Login is two steps, because the backend's `/api/auth/login` only validates the
password and sends an email OTP - it does not return tokens. Real tokens only
come back from `/api/auth/verify-login-otp`. `features/auth/login-form.tsx`
implements exactly that: a credentials step, then an OTP step, with no
shortcuts taken. Tokens are Bearer JWTs (no cookies, matching the backend's
stateless design), held in a Zustand store persisted to `localStorage`, and
attached by `gateway-client.ts`'s request interceptor. A single shared
in-flight refresh (`gateway-client.ts`) prevents duplicate `/refresh` calls
when several requests 401 at once.

## Live updates

The Dashboard's "Live Activity" card consumes `GET /api/live/events`
(admin-only SSE, fed by the platform-wide RabbitMQ domain-event exchange).
Native `EventSource` can't set an `Authorization` header and can only
listen for event names known ahead of time, so `lib/api/sse.ts` is a small
hand-rolled `fetch` + `ReadableStream` parser instead - it stays generic
across every event type the platform emits, present or future, without a
hardcoded list. See `hooks/use-live-updates.ts`.

## Module status

| Module | Status |
|---|---|
| Dashboard | ✅ Built - platform health, alerts, deployments, jobs, cache, metrics, dependency status, live activity feed |
| Authentication → API Keys | Backend ready, not yet built |
| Authentication → Users/Roles/Permissions/Sessions | ❌ No backend endpoint (see gaps doc) |
| Notifications → History | Backend ready, not yet built |
| Notifications → Templates/Queue | ❌ No backend endpoint |
| Audit | Backend ready, not yet built |
| Rate Limiter | Backend is internal-only (used by `@RateLimited` aspect), not a browser-facing module |
| Cache, Scheduler, Feature Flags, Monitoring, Deployment | Backend ready and now gatewayed - not yet built as full modules (service layer already in place) |
| Settings | Not yet scoped |

Next up, module by module, in the order agreed: Authentication (API Keys
first, then Users/Roles/Permissions/Sessions once/if the backend adds
those endpoints), then Notifications, then the rest.
