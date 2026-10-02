package com.arijit.authservice.entity;

/**
 * Only meaningful for a project-scoped {@link ApiKey} (architecture design
 * §7) - a platform-level key has no {@code keyType} at all. Mirrors the
 * Stripe/Firebase public-vs-secret-key convention developers already
 * expect: a PUBLIC key is safe to embed in client-side code (a generated
 * SDK's default config - see the design doc's §9), a SECRET key is for
 * server-side callers only and should never be shipped to a browser or
 * mobile app.
 */
public enum ApiKeyType {
    PUBLIC,
    SECRET
}
