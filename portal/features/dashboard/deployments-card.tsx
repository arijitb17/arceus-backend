"use client";

import { Rocket } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { formatDateTime } from "@/lib/utils";
import { useRecentDeployments } from "@/hooks/use-dashboard";
import { getErrorMessage } from "@/lib/api/error";

export function DeploymentsCard() {
  const { data, isLoading, isError, error, refetch } = useRecentDeployments(5);
  const deployments = data?.items ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Rocket className="size-4 text-muted" />
          Recent Deployments
        </CardTitle>
        <CardDescription>Latest releases across every environment</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="space-y-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        )}

        {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

        {!isLoading && !isError && deployments.length === 0 && (
          <EmptyState icon={Rocket} title="No deployments yet" description="New releases will show up here." />
        )}

        {!isLoading && deployments.length > 0 && (
          <ul className="divide-y divide-border">
            {deployments.map((d) => (
              <li key={d.id} className="flex items-center justify-between gap-3 py-2.5 first:pt-0 last:pb-0">
                <div className="min-w-0 space-y-0.5">
                  <p className="text-sm font-medium text-foreground truncate">
                    {d.serviceName} <span className="font-data text-muted">{d.serviceVersion}</span>
                  </p>
                  <p className="text-[11px] text-muted-foreground">
                    {d.environment} · {formatDateTime(d.updatedAt)}
                  </p>
                </div>
                <StatusBadge status={d.status} className="shrink-0" />
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
