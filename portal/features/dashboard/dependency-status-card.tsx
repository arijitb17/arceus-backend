"use client";

import { Database, MessageSquareShare, HardDrive } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge } from "@/components/shared/status-badge";
import { usePlatformDashboard } from "@/hooks/use-dashboard";
import type { ServiceHealthDto } from "@/types/monitoring";

/**
 * A dependency is UP only if every service that actually uses it reports
 * UP. NOT_APPLICABLE (a service with no Postgres/Redis/RabbitMQ
 * dependency at all - see ComponentStatus's javadoc) and UNKNOWN (not
 * checked yet) are excluded before aggregating, not treated as "not UP" -
 * otherwise every dependency would permanently show DEGRADED, since there
 * is always at least one service that legitimately doesn't use it.
 */
function aggregate(services: ServiceHealthDto[], pick: (s: ServiceHealthDto) => string) {
  const values = services
    .map(pick)
    .filter(Boolean)
    .map((v) => v.toUpperCase())
    .filter((v) => v !== "NOT_APPLICABLE" && v !== "UNKNOWN");
  if (values.length === 0) return "UNKNOWN";
  if (values.some((v) => v === "DOWN")) return "DOWN";
  if (values.some((v) => v !== "UP")) return "DEGRADED";
  return "UP";
}

export function DependencyStatusCard() {
  const { data, isLoading } = usePlatformDashboard();

  const rows = data
    ? [
        {
          label: "PostgreSQL Database",
          icon: Database,
          status: aggregate(data.services, (s) => s.databaseStatus),
        },
        {
          label: "Redis Cache",
          icon: HardDrive,
          status: aggregate(data.services, (s) => s.cacheStatus),
        },
        {
          label: "RabbitMQ",
          icon: MessageSquareShare,
          status: aggregate(data.services, (s) => s.rabbitStatus),
        },
      ]
    : [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Infrastructure Dependencies</CardTitle>
        <CardDescription>Aggregated from every service&apos;s health check</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {isLoading &&
          Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-9" />)}

        {!isLoading &&
          rows.map((row) => (
            <div
              key={row.label}
              className="flex items-center justify-between rounded-md border border-border bg-surface px-3 py-2"
            >
              <div className="flex items-center gap-2.5">
                <row.icon className="size-4 text-muted" />
                <span className="text-sm text-foreground">{row.label}</span>
              </div>
              <StatusBadge status={row.status} />
            </div>
          ))}
      </CardContent>
    </Card>
  );
}
