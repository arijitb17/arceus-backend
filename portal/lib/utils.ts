import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Formats an ISO/Instant string as a short, absolute local timestamp. */
export function formatDateTime(value: string | number | Date | null | undefined): string {
  if (!value) return "\u2014";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "\u2014";
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Formats a duration in milliseconds as a compact human string, e.g. "1.2s", "340ms". */
export function formatDuration(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return "\u2014";
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  return `${(ms / 60_000).toFixed(1)}m`;
}

/** Truncates a long id/key to a short readable prefix, e.g. for table cells. */
export function truncateId(value: string, length = 8): string {
  if (!value) return "";
  return value.length <= length ? value : `${value.slice(0, length)}\u2026`;
}
