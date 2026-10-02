"use client";

import { useState } from "react";
import { Activity, ServerCog, ShieldAlert, Gauge } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { StatCard } from "@/components/shared/stat-card";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { StatusBadge } from "@/components/shared/status-badge";
import { Badge } from "@/components/ui/badge";
import { AlertRuleDialog } from "@/features/monitoring/alert-rule-dialog";
import { useServiceHealth, useAlertRules, useDeleteAlertRule, useMonitoringStats } from "@/hooks/use-monitoring";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime, formatDuration } from "@/lib/utils";

export default function MonitoringPage() {
  const { data: stats, isLoading: statsLoading } = useMonitoringStats();
  const { data: health, isLoading: healthLoading, isError: healthError, error: healthErrorObj, refetch: refetchHealth } = useServiceHealth();
  const { data: rules, isLoading: rulesLoading, isError: rulesError, error: rulesErrorObj, refetch: refetchRules } = useAlertRules();
  const deleteRule = useDeleteAlertRule();
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null);

  return (
    <AppShell title="Monitoring">
      <div className="space-y-6">
        <PageHeader title="Monitoring" description="Live service health, resource usage, and alert rules across the platform" />

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard
            label="Services Up"
            value={stats ? `${stats.servicesUp}/${stats.totalServices}` : "\u2014"}
            icon={ServerCog}
            tone={stats && stats.servicesDown > 0 ? "error" : "success"}
            loading={statsLoading}
          />
          <StatCard
            label="Active Alerts"
            value={stats ? String(stats.activeAlertsCount) : "\u2014"}
            icon={ShieldAlert}
            tone={stats && stats.activeAlertsCount > 0 ? "warning" : "default"}
            loading={statsLoading}
          />
          <StatCard
            label="Platform Availability"
            value={stats?.platformAvailabilityPercentage != null ? `${stats.platformAvailabilityPercentage.toFixed(2)}%` : "\u2014"}
            icon={Gauge}
            tone="primary"
            loading={statsLoading}
          />
          <StatCard
            label="Avg Response Time"
            value={stats?.avgPlatformResponseTimeMs != null ? `${Math.round(stats.avgPlatformResponseTimeMs)}ms` : "\u2014"}
            icon={Activity}
            tone="accent"
            loading={statsLoading}
          />
        </div>

        <Tabs defaultValue="services">
          <TabsList>
            <TabsTrigger value="services">Services</TabsTrigger>
            <TabsTrigger value="alerts">Alert Rules</TabsTrigger>
          </TabsList>

          <TabsContent value="services">
            <Card>
              {healthLoading && (
                <CardContent className="space-y-2">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <Skeleton key={i} className="h-10" />
                  ))}
                </CardContent>
              )}
              {healthError && (
                <CardContent>
                  <ErrorState message={getErrorMessage(healthErrorObj)} onRetry={() => refetchHealth()} />
                </CardContent>
              )}
              {health && health.services.length === 0 && (
                <CardContent>
                  <EmptyState icon={ServerCog} title="No services reporting" />
                </CardContent>
              )}
              {health && health.services.length > 0 && (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Service</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>DB</TableHead>
                      <TableHead>Cache</TableHead>
                      <TableHead>RabbitMQ</TableHead>
                      <TableHead>gRPC</TableHead>
                      <TableHead>Response</TableHead>
                      <TableHead>Version</TableHead>
                      <TableHead>Checked</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {health.services.map((s) => (
                      <TableRow key={s.serviceName}>
                        <TableCell className="font-data">{s.serviceName}</TableCell>
                        <TableCell>
                          <StatusBadge status={s.status} />
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{s.databaseStatus}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{s.cacheStatus}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{s.rabbitStatus}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{s.grpcStatus}</Badge>
                        </TableCell>
                        <TableCell className="text-muted tabular-nums">{formatDuration(s.responseTimeMs)}</TableCell>
                        <TableCell className="text-muted font-data">{s.version}</TableCell>
                        <TableCell className="text-muted">{formatDateTime(s.checkedAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Card>
          </TabsContent>

          <TabsContent value="alerts">
            <Card>
              <div className="flex items-center justify-between border-b border-border p-3">
                <p className="text-xs text-muted">Rules that generate alert events when their threshold is crossed</p>
                <AlertRuleDialog />
              </div>
              {rulesLoading && (
                <CardContent className="space-y-2">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <Skeleton key={i} className="h-10" />
                  ))}
                </CardContent>
              )}
              {rulesError && (
                <CardContent>
                  <ErrorState message={getErrorMessage(rulesErrorObj)} onRetry={() => refetchRules()} />
                </CardContent>
              )}
              {rules && rules.items.length === 0 && (
                <CardContent>
                  <EmptyState icon={ShieldAlert} title="No alert rules configured" description="Create one to start getting notified on service issues." />
                </CardContent>
              )}
              {rules && rules.items.length > 0 && (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Name</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Service</TableHead>
                      <TableHead>Threshold</TableHead>
                      <TableHead>Severity</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rules.items.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell className="font-data">{r.name}</TableCell>
                        <TableCell>
                          <Badge variant="outline">{r.type}</Badge>
                        </TableCell>
                        <TableCell className="text-muted">{r.serviceName || "platform-wide"}</TableCell>
                        <TableCell className="text-muted tabular-nums">{r.thresholdValue}</TableCell>
                        <TableCell>
                          <Badge variant={r.severity === "CRITICAL" ? "error" : r.severity === "WARNING" ? "warning" : "default"}>{r.severity}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={r.enabled ? "success" : "default"} dot>
                            {r.enabled ? "Enabled" : "Disabled"}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-1">
                            <AlertRuleDialog rule={r} />
                            <Button variant="ghost" size="sm" onClick={() => setDeleteTarget({ id: r.id, name: r.name })}>
                              Delete
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title={`Delete "${deleteTarget?.name}"?`}
        description="This can't be undone. Future threshold breaches for this rule will no longer generate alerts."
        loading={deleteRule.isPending}
        onConfirm={() => {
          if (deleteTarget) deleteRule.mutate(deleteTarget.id, { onSuccess: () => setDeleteTarget(null) });
        }}
      />
    </AppShell>
  );
}
