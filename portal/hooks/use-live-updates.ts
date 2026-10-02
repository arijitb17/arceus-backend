"use client";

import { useEffect, useRef, useState } from "react";
import { config } from "@/lib/config";
import { streamSse } from "@/lib/api/sse";
import { useAuthStore } from "@/store/auth-store";

export interface LiveEvent {
  eventId: string;
  eventType: string;
  occurredAt: string;
  sourceService: string;
  payload: Record<string, unknown>;
}

export type LiveConnectionState = "connecting" | "open" | "closed" | "unauthorized";

const MAX_EVENTS = 30;
const RECONNECT_DELAY_MS = 4000;

/**
 * GET /api/live/events (admin-only SSE) - one shared connection for the
 * whole app. Reconnects with a fixed backoff on any drop; a 403 means the
 * signed-in user isn't ROLE_ADMIN, so we stop retrying rather than loop.
 */
export function useLiveUpdates() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const isAdmin = useAuthStore((s) => s.user?.roles?.includes("ROLE_ADMIN") ?? false);
  const [events, setEvents] = useState<LiveEvent[]>([]);
  const [state, setState] = useState<LiveConnectionState>("connecting");

  const eventsRef = useRef(events);
  eventsRef.current = events;

  useEffect(() => {
    if (!accessToken || !isAdmin) {
      setState("closed");
      return;
    }

    const controller = new AbortController();
    let cancelled = false;
    let retryTimer: ReturnType<typeof setTimeout>;

    async function connect() {
      setState("connecting");
      try {
        await streamSse(
          `${config.gatewayUrl}/api/live/events`,
          accessToken!,
          (raw) => {
            setState("open");
            if (raw.event === "heartbeat") {
              // Proves the connection is alive even when nothing else has
              // happened - see SseEmitterRegistry's javadoc - but it's not
              // a domain event, so it doesn't belong in the visible feed.
              return;
            }
            try {
              const parsed = JSON.parse(raw.data);
              setEvents((prev) => [
                {
                  eventId: parsed.eventId,
                  eventType: raw.event,
                  occurredAt: parsed.occurredAt,
                  sourceService: parsed.sourceService,
                  payload: parsed.payload ?? {},
                },
                ...prev,
              ].slice(0, MAX_EVENTS));
            } catch {
              // Non-JSON frame - ignore.
            }
          },
          controller.signal
        );
      } catch (err) {
        if (cancelled) return;
        const message = err instanceof Error ? err.message : "";
        if (message.includes("403")) {
          setState("unauthorized");
          return;
        }
      }
      if (!cancelled) {
        setState("closed");
        retryTimer = setTimeout(connect, RECONNECT_DELAY_MS);
      }
    }

    connect();

    return () => {
      cancelled = true;
      controller.abort();
      clearTimeout(retryTimer);
    };
  }, [accessToken, isAdmin]);

  return { events, state };
}
