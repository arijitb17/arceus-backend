package com.arijit.arceus.events.domain;

/**
 * The one place both ends of "correlation id" agree on a name, so the
 * header the Gateway sets, the MDC key each service logs with, and the
 * field audit-service stores are never a copy-pasted string that drifts.
 * Deliberately dependency-light (no Slf4j/servlet types) so this module
 * stays usable from anything, per its own module description.
 */
public final class CorrelationConstants {

    /** Wire header carrying the correlation id between client -> Gateway -> downstream services. */
    public static final String HEADER_NAME = "X-Request-Id";

    /** Key every service should use when putting the correlation id into SLF4J's MDC. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationConstants() {}
}
