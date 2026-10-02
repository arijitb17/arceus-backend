# Arceus Portal

One Next.js app that serves two audiences, split by role:

- **Every signed-in user** gets the developer workspace: `/organizations` and everything under it
  (projects, modules, secrets, project API keys, SDK config, usage), plus `/settings` and personal
  `/auth/api-keys`.
- **`ROLE_ADMIN`** additionally gets the operator console: Dashboard, Users / Roles / Permissions /
  Sessions, Notifications, Rate Limiter, Cache, Scheduler, Feature Flags and Monitoring.

Stack: Next.js 15 (App Router), React 19, TypeScript, Tailwind, TanStack Query, React Hook Form,
Zod, Zustand, Recharts.

## Run it

```bash
npm install
cp .env.local.example .env.local   # NEXT_PUBLIC_GATEWAY_URL, default http://localhost:8090
npm run dev                        # http://localhost:3000
```

The portal talks only to `gateway-service`. The Gateway's CORS allow-list is empty by default, so
set `GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:3000` on the backend. With `docker compose up`
this is already configured.

`NEXT_PUBLIC_*` values are baked in at build time; the Dockerfile takes it as a build arg.

## Pages

| Area | Route | Access |
|---|---|---|
| Dashboard (health, alerts, jobs, cache, metrics, live activity) | `/dashboard` | Admin |
| Organizations, projects, modules, secrets, API keys, SDK config, usage | `/organizations/**` | Any user |
| Users, Roles, Permissions, Sessions | `/auth/*` | Admin |
| Personal API keys | `/auth/api-keys` | Any user |
| Notifications: templates, history, queue | `/notifications/*` | Admin |
| Cache, Scheduler, Feature Flags, Monitoring | `/cache`, `/scheduler`, `/feature-flags`, `/monitoring` | Admin |
| Rate Limiter (explains why it has no UI: the service is internal-only) | `/rate-limiter` | Admin |
| Settings | `/settings` | Any user |

## Role-based access

Enforced in the route, not just the menu:

- `lib/rbac.ts` lists the admin-only route prefixes: the single source of truth.
- `lib/nav-config.ts` tags each sidebar entry with its allowed roles; `features/layout/sidebar.tsx`
  filters by them.
- `features/layout/app-shell.tsx` re-checks the current URL and redirects a non-admin away from
  admin paths (typed in, bookmarked, etc.).
- After login, users land on `/dashboard` (admin) or `/organizations` (everyone else).

## Auth flow

Login is two steps because `/api/auth/login` only validates the password and sends an email OTP;
tokens come from `/api/auth/verify-login-otp`. Tokens are Bearer JWTs held in a Zustand store
persisted to `localStorage`, attached by `lib/api/gateway-client.ts`. A single shared in-flight
refresh prevents duplicate `/refresh` calls when several requests return 401 together.

## Live updates

The dashboard's Live Activity card consumes `GET /api/live/events` (admin-only SSE fed by the
platform event exchange). Native `EventSource` cannot send an `Authorization` header, so
`lib/api/sse.ts` is a small `fetch` + `ReadableStream` parser; see `hooks/use-live-updates.ts`.

## Structure

```
app/          Routes (App Router)
components/   ui/ (shadcn-style primitives) and shared/ (StatCard, StatusBadge, ...)
features/     One folder per feature: dashboard, auth, organizations, projects, modules,
              secrets, api-keys, sdk-config, usage, cache, feature-flags, monitoring,
              notifications, rbac, layout
hooks/        TanStack Query hooks, one file per domain
services/     Thin API wrappers, one per backend service
lib/          gateway-client, sse, config, nav-config, rbac, utils
store/        Zustand auth store
types/        Types mirroring the backend DTOs
```

## Design tokens

Dark-first. Background `#0B1220`, surface `#111827`, card `#1F2937`, border `#374151`, primary
`#2563EB`, accent `#7C3AED`, success / warning / error `#10B981` / `#F59E0B` / `#EF4444`, text
`#F9FAFB`. All defined in `tailwind.config.ts`.
