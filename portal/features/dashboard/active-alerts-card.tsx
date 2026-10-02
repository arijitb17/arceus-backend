"use client";

import { Siren } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Badge } from "@/components/ui/badge";
import { formatDateTime } from "@/lib/utils";
import { usePlatformDashboard } from "@/hooks/use-dashboard";
import { getErrorMessage } from "@/lib/api/error";

const severityVariant = {
  CRITICAL: "error",
  WARNING: "warning",
  INFO: "primary",
} as const;

export function ActiveAlertsCard() {
  const { data, isLoading, isError, error, refetch } = usePlatformDashboard();
  const alerts = data?.activeAlerts ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Siren className="size-4 text-muted" />
          Active Alerts
        </CardTitle>
        <CardDescription>Currently firing across the platform</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        )}

        {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

        {!isLoading && !isError && alerts.length === 0 && (
          <EmptyState icon={Siren} title="No active alerts" description="Everything is within configured thresholds." />
        )}

        {!isLoading && alerts.length > 0 && (
          <ul className="space-y-2">
            {alerts.map((alert) => (
              <li
                key={alert.id}
                className="flex items-start justify-between gap-3 rounded-md border border-border bg-surface px-3 py-2.5"
              >
                <div className="min-w-0 space-y-0.5">
                  <p className="text-sm font-medium text-foreground truncate">{alert.ruleName}</p>
                  <p className="text-xs text-muted truncate">{alert.message}</p>
                  <p className="text-[11px] text-muted-foreground">
                    {alert.serviceName ?? "Platform-wide"} · {formatDateTime(alert.triggeredAt)}
                  </p>
                </div>
                <Badge
                  variant={severityVariant[alert.severity as keyof typeof severityVariant] ?? "default"}
                  className="shrink-0"
                >
                  {alert.severity}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
