"use client";

import { Radio } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/empty-error-state";
import { LiveIndicator } from "@/components/shared/live-indicator";
import { useLiveUpdates } from "@/hooks/use-live-updates";

function relativeTime(iso: string) {
  const diffMs = Date.now() - new Date(iso).getTime();
  const seconds = Math.max(0, Math.floor(diffMs / 1000));
  if (seconds < 5) return "just now";
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ago`;
}

function eventLabel(eventType: string) {
  return eventType
    .toLowerCase()
    .split("_")
    .map((w) => w[0]?.toUpperCase() + w.slice(1))
    .join(" ");
}

export function LiveActivityCard() {
  const { events, state } = useLiveUpdates();

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <div>
          <CardTitle className="flex items-center gap-2">
            <Radio className="size-4 text-muted" />
            Live Activity
          </CardTitle>
          <CardDescription>Real-time platform event stream</CardDescription>
        </div>
        <LiveIndicator state={state} />
      </CardHeader>
      <CardContent>
        {state === "unauthorized" && (
          <EmptyState icon={Radio} title="Admin access required" description="Sign in as an admin to view the live event stream." />
        )}

        {state !== "unauthorized" && events.length === 0 && (
          <EmptyState icon={Radio} title="Waiting for activity" description="Events will appear here the moment they happen." />
        )}

        {events.length > 0 && (
          <ul className="space-y-1 max-h-72 overflow-y-auto pr-1">
            {events.map((e) => (
              <li
                key={e.eventId}
                className="flex items-center justify-between gap-3 rounded-md px-2.5 py-2 text-sm hover:bg-surface transition-colors animate-fade-in"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <span className="size-1.5 shrink-0 rounded-full bg-primary" />
                  <div className="min-w-0">
                    <p className="text-foreground truncate">{eventLabel(e.eventType)}</p>
                    <p className="text-[11px] text-muted-foreground truncate">{e.sourceService}</p>
                  </div>
                </div>
                <span className="shrink-0 text-[11px] text-muted-foreground tabular-nums">
                  {relativeTime(e.occurredAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
