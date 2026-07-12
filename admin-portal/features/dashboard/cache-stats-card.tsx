"use client";

import { Database } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/shared/empty-error-state";
import { useCacheStats } from "@/hooks/use-dashboard";
import { getErrorMessage } from "@/lib/api/error";

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

export function CacheStatsCard() {
  const { data, isLoading, isError, error, refetch } = useCacheStats();

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Database className="size-4 text-muted" />
          Cache Statistics
        </CardTitle>
        <CardDescription>Redis-backed cache layer</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading && <Skeleton className="h-24" />}
        {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

        {data && (
          <>
            <div>
              <div className="flex items-center justify-between text-xs text-muted mb-1.5">
                <span>Hit ratio</span>
                <span className="font-medium text-foreground tabular-nums">
                  {(data.hitRatio * 100).toFixed(1)}%
                </span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface">
                <div
                  className="h-full rounded-full bg-success"
                  style={{ width: `${Math.min(data.hitRatio * 100, 100)}%` }}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-md border border-border bg-surface px-3 py-2">
                <p className="text-[11px] text-muted">Total Keys</p>
                <p className="text-base font-semibold tabular-nums">{data.totalKeys.toLocaleString()}</p>
              </div>
              <div className="rounded-md border border-border bg-surface px-3 py-2">
                <p className="text-[11px] text-muted">Memory Used</p>
                <p className="text-base font-semibold tabular-nums">{formatBytes(data.memoryUsageBytes)}</p>
              </div>
              <div className="rounded-md border border-border bg-surface px-3 py-2">
                <p className="text-[11px] text-muted">Evictions</p>
                <p className="text-base font-semibold tabular-nums">{data.evictionCount.toLocaleString()}</p>
              </div>
              <div className="rounded-md border border-border bg-surface px-3 py-2">
                <p className="text-[11px] text-muted">Expired Keys</p>
                <p className="text-base font-semibold tabular-nums">{data.expiredKeys.toLocaleString()}</p>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
