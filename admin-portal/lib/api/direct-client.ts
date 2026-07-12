import axios, { type AxiosInstance } from "axios";
import { authStore } from "@/store/auth-store";

/**
 * ⚠️ TEMPORARY DEVELOPMENT SHIM - read before touching this file.
 *
 * monitoring-service, deployment-service, scheduler-service,
 * feature-flag-service, and cache-service are NOT proxied by
 * gateway-service in the current backend (see lib/config.ts and
 * README_BACKEND_GAPS.md). They live on the internal network and expect
 * either:
 *   - X-User-Email / X-User-Roles headers, which the real Gateway sets
 *     *after cryptographically verifying* a JWT, or
 *   - an X-Internal-Token shared secret, for service-to-service calls.
 *
 * This client fakes the first path by decoding (NOT verifying) the JWT
 * payload already sitting in the auth store and copying its email/roles
 * into those headers. That is fine for local development against a
 * backend that also isn't hardened yet, but it means anyone with browser
 * devtools could set those headers to whatever they want and this client
 * would happily send them - there is no cryptographic guarantee here.
 *
 * DO NOT ship this to production. The real fix is adding these five
 * services to gateway-service's ProxyController (see ProxyController.java's
 * Route enum) the same way Auth/Notifications/Audit are wired, then
 * deleting this file and pointing every service/* module at
 * gateway-client.ts instead.
 */

function decodeJwtPayload(token: string): { sub?: string; roles?: string[] } | null {
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

let hasWarned = false;

export function createDirectClient(baseURL: string, serviceLabel: string): AxiosInstance {
  const client = axios.create({ baseURL, headers: { "Content-Type": "application/json" } });

  client.interceptors.request.use((requestConfig) => {
    if (!hasWarned && typeof window !== "undefined") {
      hasWarned = true;
      // eslint-disable-next-line no-console
      console.warn(
        "[arceus] Calling non-gatewayed services directly (dev shim) - see lib/api/direct-client.ts"
      );
    }

    const { accessToken } = authStore.getState();
    if (accessToken) {
      const claims = decodeJwtPayload(accessToken);
      if (claims?.sub) requestConfig.headers["X-User-Email"] = claims.sub;
      if (claims?.roles) requestConfig.headers["X-User-Roles"] = claims.roles.join(",");
    }
    requestConfig.headers["X-Direct-Client-Warning"] = serviceLabel;
    return requestConfig;
  });

  return client;
}
