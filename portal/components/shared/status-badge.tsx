import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const SUCCESS_STATUSES = new Set([
  "UP",
  "RUNNING",
  "COMPLETED",
  "SUCCESS",
  "APPROVED",
  "RESOLVED",
]);
const ERROR_STATUSES = new Set(["DOWN", "FAILED", "CANCELLED", "ROLLED_BACK", "OFFLINE"]);
const WARNING_STATUSES = new Set([
  "DEGRADED",
  "PENDING",
  "RETRYING",
  "WARNING",
  "SCHEDULED",
  "DEPLOYING",
  "PAUSED",
  "EXPIRED",
]);

export function statusVariant(status: string | null | undefined) {
  const s = (status ?? "").toUpperCase();
  if (SUCCESS_STATUSES.has(s)) return "success" as const;
  if (ERROR_STATUSES.has(s)) return "error" as const;
  if (WARNING_STATUSES.has(s)) return "warning" as const;
  return "default" as const;
}

export function StatusBadge({ status, className }: { status: string | null | undefined; className?: string }) {
  return (
    <Badge variant={statusVariant(status)} dot className={cn("font-medium", className)}>
      {status ?? "UNKNOWN"}
    </Badge>
  );
}
