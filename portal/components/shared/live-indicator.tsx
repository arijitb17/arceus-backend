"use client";

import { cn } from "@/lib/utils";
import type { LiveConnectionState } from "@/hooks/use-live-updates";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

const stateConfig: Record<LiveConnectionState, { label: string; dot: string; pulse: boolean }> = {
  open: { label: "Live", dot: "bg-success", pulse: true },
  connecting: { label: "Connecting\u2026", dot: "bg-warning", pulse: true },
  closed: { label: "Reconnecting\u2026", dot: "bg-warning", pulse: true },
  unauthorized: { label: "Live updates need admin access", dot: "bg-muted", pulse: false },
};

export function LiveIndicator({ state }: { state: LiveConnectionState }) {
  const cfg = stateConfig[state];
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className="flex items-center gap-1.5 rounded-full border border-border bg-surface px-2.5 py-1">
          <span className="relative flex size-1.5">
            {cfg.pulse && (
              <span className={cn("absolute inline-flex h-full w-full animate-ping rounded-full opacity-60", cfg.dot)} />
            )}
            <span className={cn("relative inline-flex size-1.5 rounded-full", cfg.dot)} />
          </span>
          <span className="text-[11px] font-medium text-muted">{cfg.label}</span>
        </div>
      </TooltipTrigger>
      <TooltipContent>Real-time platform event stream</TooltipContent>
    </Tooltip>
  );
}
