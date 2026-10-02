"use client";

import { Activity } from "lucide-react";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/shared/empty-error-state";
import { usePlatformDashboard } from "@/hooks/use-dashboard";
import { getErrorMessage } from "@/lib/api/error";

function ChartTooltip({ active, payload, label }: { active?: boolean; payload?: { value: number }[]; label?: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-border bg-popover px-2.5 py-1.5 text-xs shadow-elevated">
      <p className="font-medium text-foreground">{label}</p>
      <p className="text-muted">{payload[0].value}ms</p>
    </div>
  );
}

export function RequestMetricsCard() {
  const { data, isLoading, isError, error, refetch } = usePlatformDashboard();

  const chartData =
    data?.services.map((s) => ({ name: s.serviceName.replace("-service", ""), responseTimeMs: s.responseTimeMs })) ?? [];

  return (
    <Card className="lg:col-span-2">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Activity className="size-4 text-muted" />
          Request Metrics
        </CardTitle>
        <CardDescription>Platform-wide throughput and per-service response time</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && <Skeleton className="h-56" />}
        {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

        {data && (
          <>
            <div className="grid grid-cols-2 gap-3 mb-5 sm:grid-cols-4">
              <MetricTile label="Total Requests" value={data.platformMetrics.totalRequestCount.toLocaleString()} />
              <MetricTile label="Total Errors" value={data.platformMetrics.totalErrorCount.toLocaleString()} />
              <MetricTile
                label="Avg Success Rate"
                value={
                  data.platformMetrics.avgSuccessRate !== null
                    ? `${data.platformMetrics.avgSuccessRate.toFixed(1)}%`
                    : "\u2014"
                }
              />
              <MetricTile
                label="Avg Response Time"
                value={
                  data.platformMetrics.avgResponseTimeMs !== null
                    ? `${Math.round(data.platformMetrics.avgResponseTimeMs)}ms`
                    : "\u2014"
                }
              />
            </div>

            {chartData.length > 0 ? (
              <div className="h-56">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
                    <XAxis
                      dataKey="name"
                      tick={{ fill: "#9C9AA1", fontSize: 11 }}
                      axisLine={{ stroke: "#34363E" }}
                      tickLine={false}
                    />
                    <YAxis tick={{ fill: "#9C9AA1", fontSize: 11 }} axisLine={false} tickLine={false} />
                    <Tooltip content={<ChartTooltip />} cursor={{ fill: "#25272E" }} />
                    <Bar dataKey="responseTimeMs" fill="#EAB63C" radius={[4, 4, 0, 0]} maxBarSize={40} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <p className="py-10 text-center text-sm text-muted">No per-service metrics reported yet.</p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function MetricTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-border bg-surface px-3 py-2.5">
      <p className="text-[11px] text-muted">{label}</p>
      <p className="text-lg font-semibold tabular-nums text-foreground">{value}</p>
    </div>
  );
}
