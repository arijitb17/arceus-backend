"use client";

import { useMemo } from "react";
import { Activity, TrendingUp, AlertTriangle } from "lucide-react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardContent } from "@/components/ui/card";
import { StatCard } from "@/components/shared/stat-card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { useProjectUsage } from "@/hooks/use-usage";
import { getErrorMessage } from "@/lib/api/error";
import type { EnvironmentType } from "@/types/project";

export function UsagePanel({
  organizationId,
  projectId,
  environment,
}: {
  organizationId: string;
  projectId: string;
  environment: EnvironmentType;
}) {
  const { data, isLoading, isError, error, refetch } = useProjectUsage(organizationId, projectId, environment);

  const dailyTotals = useMemo(() => {
    if (!data) return [];
    const byDay = new Map<string, number>();
    for (const point of data.points) {
      byDay.set(point.day, (byDay.get(point.day) ?? 0) + point.requestCount);
    }
    return Array.from(byDay.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([day, requests]) => ({ day: day.slice(5), requests }));
  }, [data]);

  const byRoute = useMemo(() => {
    if (!data) return [];
    const totals = new Map<string, number>();
    for (const point of data.points) {
      totals.set(point.route, (totals.get(point.route) ?? 0) + point.requestCount);
    }
    return Array.from(totals.entries()).sort(([, a], [, b]) => b - a);
  }, [data]);

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
        <Skeleton className="h-64 sm:col-span-3" />
      </div>
    );
  }

  if (isError) return <ErrorState message={getErrorMessage(error)} onRetry={refetch} />;

  if (data && data.totalRequests === 0) {
    return (
      <EmptyState
        icon={Activity}
        title="No usage yet"
        description="Calls made with this project's API keys will show up here - only SDK/API traffic counts, not Portal browsing."
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label="Total requests" value={data?.totalRequests.toLocaleString() ?? "0"} icon={Activity} tone="primary" />
        <StatCard label="Errors" value={data?.totalErrors.toLocaleString() ?? "0"} icon={AlertTriangle} tone="error" />
        <StatCard
          label="Error rate"
          value={data && data.totalRequests > 0 ? `${((data.totalErrors / data.totalRequests) * 100).toFixed(1)}%` : "0%"}
          icon={TrendingUp}
        />
      </div>

      <Card>
        <CardContent className="p-5">
          <h3 className="text-sm font-medium text-foreground mb-4">Requests per day</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={dailyTotals}>
                <defs>
                  <linearGradient id="usageGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#EAB63C" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#EAB63C" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" />
                <YAxis tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" allowDecimals={false} />
                <Tooltip
                  contentStyle={{ background: "var(--surface)", border: "1px solid var(--border)", fontSize: 12 }}
                />
                <Area type="monotone" dataKey="requests" stroke="#EAB63C" fill="url(#usageGradient)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-5 space-y-3">
          <h3 className="text-sm font-medium text-foreground">By module</h3>
          <div className="space-y-2">
            {byRoute.map(([route, count]) => (
              <div key={route} className="flex items-center justify-between text-sm">
                <span className="text-muted">{route}</span>
                <span className="font-data text-foreground">{count.toLocaleString()}</span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
