/**
 * The portal talks to exactly one backend: gateway-service (default :8090).
 * Auth, Notifications, Monitoring, Scheduler, Feature Flags, Cache and
 * Organizations are each proxied under their own /api/<service> prefix, plus
 * an admin-only SSE stream at /api/live/events. ratelimit-service stays
 * internal-only and is intentionally not called from here.
 *
 * See services/*.service.ts for how each domain's paths map onto the
 * Gateway's ProxyController routes.
 */
export const config = {
  gatewayUrl: process.env.NEXT_PUBLIC_GATEWAY_URL ?? "http://localhost:8090",
} as const;
