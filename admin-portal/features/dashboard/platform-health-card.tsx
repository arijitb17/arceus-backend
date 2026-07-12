"use client";

import { HeartPulse } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/shared/empty-error-state";
import { StatusBadge, statusVariant } from "@/components/shared/status-badge";
import { cn, formatDateTime } from "@/lib/utils";
import { usePlatformDashboard } from "@/hooks/use-dashboard";
import { getErrorMessage } from "@/lib/api/error";

export function PlatformHealthCard() {
  const { data, isLoading, isError, error, refetch } = usePlatformDashboard();

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <div>
          <CardTitle className="flex items-center gap-2">
            <HeartPulse className="size-4 text-muted" />
            Platform Health
          </CardTitle>
          <CardDescription>Live status across every registered service</CardDescription>
        </div>
        {data && <StatusBadge status={data.overallStatus} />}
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-16" />
            ))}
          </div>
        )}

        {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

        {data && data.services.length === 0 && (
          <p className="py-6 text-center text-sm text-muted">No services reporting health yet.</p>
        )}

        {data && data.services.length > 0 && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {data.services.map((svc) => (
              <div
                key={svc.serviceName}
                className={cn(
                  "rounded-md border border-border bg-surface p-3 space-y-1.5",
                  statusVariant(svc.status) === "error" && "border-error/40"
                )}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="text-xs font-medium text-foreground truncate">{svc.serviceName}</p>
                  <span
                    className={cn("size-1.5 shrink-0 rounded-full", {
                      "bg-success": statusVariant(svc.status) === "success",
                      "bg-error": statusVariant(svc.status) === "error",
                      "bg-warning": statusVariant(svc.status) === "warning",
                      "bg-muted": statusVariant(svc.status) === "default",
                    })}
                  />
                </div>
                <p className="text-[11px] text-muted">{svc.responseTimeMs}ms · v{svc.version || "\u2014"}</p>
              </div>
            ))}
          </div>
        )}

        {data && (
          <p className="mt-3 text-[11px] text-muted-foreground">
            Updated {formatDateTime(data.generatedAt)}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
