/**
 * As of the current backend, gateway-service (:8090) proxies all eight
 * REST-facing services - Auth, Notifications, Audit, Monitoring,
 * Deployments, Scheduler, Feature Flags, and Cache - each under its own
 * /api/<service> prefix, plus an admin-only SSE stream at /api/live/events.
 * ratelimit-service stays internal-only (it backs the @RateLimited aspect,
 * not a browser-facing module) and is intentionally not called here.
 *
 * This is the only base URL the frontend needs. See services/*.service.ts
 * for how each domain's paths map onto the Gateway's ProxyController.Route
 * table (a couple of them rewrite the path segment, e.g. /api/scheduler/*
 * downstream is /jobs/*, /api/deployments/* downstream is /deployments/*).
 */
export const config = {
  gatewayUrl: process.env.NEXT_PUBLIC_GATEWAY_URL ?? "http://localhost:8090",
} as const;
