package com.arijit.arceus.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Best-effort client IP resolution for the current HTTP request.
 *
 * NOTE: honors X-Forwarded-For, which means it's only trustworthy once
 * there's a reverse proxy / gateway in front of this service that sets that
 * header itself and strips/overwrites anything a client tried to send. Right
 * now, with services reachable directly, a client could spoof this header
 * to make itself look like a different IP each request and dodge perIp()
 * limiting entirely. Revisit this the moment gateway-service exists and
 * becomes the only public entry point - at that point this class is correct
 * as-is, it just needs the gateway to be trustworthy.
 */
final class ClientIpResolver {

    private ClientIpResolver() {}

    static String resolve() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return "no-request-context";
        }
        HttpServletRequest request = servletAttrs.getRequest();

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
