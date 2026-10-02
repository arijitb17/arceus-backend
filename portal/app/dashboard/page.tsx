"use client";

import { Activity, ServerCog, ShieldAlert, Gauge } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { StatCard } from "@/components/shared/stat-card";
import { PlatformHealthCard } from "@/features/dashboard/platform-health-card";
import { DependencyStatusCard } from "@/features/dashboard/dependency-status-card";
import { ActiveAlertsCard } from "@/features/dashboard/active-alerts-card";
import { RunningJobsCard } from "@/features/dashboard/running-jobs-card";
import { CacheStatsCard } from "@/features/dashboard/cache-stats-card";
import { RequestMetricsCard } from "@/features/dashboard/request-metrics-card";
import { LiveActivityCard } from "@/features/dashboard/live-activity-card";
import { usePlatformStats } from "@/hooks/use-dashboard";

export default function DashboardPage() {
  const { data: stats, isLoading } = usePlatformStats();

  return (
    <AppShell title="Dashboard">
      <div className="space-y-6">
        <div className="flex items-end justify-between">
          <div>
            <h2 className="text-2xl font-semibold text-foreground tracking-tight">Platform Overview</h2>
            <p className="text-sm text-muted mt-1">Real-time status across every Arceus service</p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard
            label="Services Up"
            value={stats ? `${stats.servicesUp}/${stats.totalServices}` : "\u2014"}
            icon={ServerCog}
            tone={stats && stats.servicesDown > 0 ? "error" : "success"}
            loading={isLoading}
          />
          <StatCard
            label="Active Alerts"
            value={stats ? String(stats.activeAlertsCount) : "\u2014"}
            icon={ShieldAlert}
            tone={stats && stats.activeAlertsCount > 0 ? "warning" : "default"}
            sublabel={stats ? `${stats.alertsTriggeredLast24h} in last 24h` : undefined}
            loading={isLoading}
          />
          <StatCard
            label="Platform Availability"
            value={stats?.platformAvailabilityPercentage != null ? `${stats.platformAvailabilityPercentage.toFixed(2)}%` : "\u2014"}
            icon={Gauge}
            tone="primary"
            loading={isLoading}
          />
          <StatCard
            label="Avg Response Time"
            value={stats?.avgPlatformResponseTimeMs != null ? `${Math.round(stats.avgPlatformResponseTimeMs)}ms` : "\u2014"}
            icon={Activity}
            tone="accent"
            loading={isLoading}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <PlatformHealthCard />
          <ActiveAlertsCard />
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <RequestMetricsCard />
          <DependencyStatusCard />
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <LiveActivityCard />
          <RunningJobsCard />
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <CacheStatsCard />
        </div>
      </div>
    </AppShell>
  );
}
