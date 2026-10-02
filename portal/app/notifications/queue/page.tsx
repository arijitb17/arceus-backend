"use client";

import { useState } from "react";
import { Inbox, Clock, RefreshCw, Skull, CheckCircle2, XCircle } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { FilterBar } from "@/components/shared/filter-bar";
import { StatCard } from "@/components/shared/stat-card";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { StatusBadge } from "@/components/shared/status-badge";
import { useNotificationQueue, useQueueSummary } from "@/hooks/use-notifications";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { NotificationChannel } from "@/types/notification";

const CHANNELS: NotificationChannel[] = ["EMAIL", "SMS", "PUSH", "SLACK", "DISCORD", "WEBHOOK"];

export default function NotificationQueuePage() {
  const [channel, setChannel] = useState<NotificationChannel | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const { data: summary, isLoading: summaryLoading } = useQueueSummary();
  const { data, isLoading, isError, error, refetch } = useNotificationQueue({
    channel: channel === "ALL" ? undefined : channel,
    page,
  });

  return (
    <AppShell title="Notification Queue">
      <div className="space-y-6">
        <PageHeader title="Queue" description="Notifications currently in-flight, retrying, or dead-lettered" />

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
          <StatCard label="Pending" value={summary ? String(summary.pending) : "\u2014"} icon={Clock} tone="warning" loading={summaryLoading} />
          <StatCard label="Retrying" value={summary ? String(summary.retrying) : "\u2014"} icon={RefreshCw} tone="warning" loading={summaryLoading} />
          <StatCard label="Dead" value={summary ? String(summary.dead) : "\u2014"} icon={Skull} tone="error" loading={summaryLoading} />
          <StatCard label="Sent" value={summary ? String(summary.sent) : "\u2014"} icon={CheckCircle2} tone="success" loading={summaryLoading} />
          <StatCard label="Failed" value={summary ? String(summary.failed) : "\u2014"} icon={XCircle} tone="error" loading={summaryLoading} />
        </div>

        <Card>
          <div className="flex items-center gap-3 border-b border-border p-3">
            <FilterBar>
              <Select
                value={channel}
                onValueChange={(v) => {
                  setChannel(v as NotificationChannel | "ALL");
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-40">
                  <SelectValue placeholder="Channel" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All channels</SelectItem>
                  {CHANNELS.map((c) => (
                    <SelectItem key={c} value={c}>
                      {c}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FilterBar>
          </div>

          {isLoading && (
            <CardContent className="space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-10" />
              ))}
            </CardContent>
          )}

          {isError && (
            <CardContent>
              <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />
            </CardContent>
          )}

          {data && data.items.length === 0 && (
            <CardContent>
              <EmptyState icon={Inbox} title="Queue is empty" description="Nothing pending, retrying, or dead-lettered right now." />
            </CardContent>
          )}

          {data && data.items.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Template</TableHead>
                    <TableHead>Channel</TableHead>
                    <TableHead>To</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Attempts</TableHead>
                    <TableHead>Last error</TableHead>
                    <TableHead>Updated</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((n) => (
                    <TableRow key={n.id}>
                      <TableCell className="font-data">{n.template}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{n.channel}</Badge>
                      </TableCell>
                      <TableCell className="text-muted">{n.to}</TableCell>
                      <TableCell>
                        <StatusBadge status={n.status} />
                      </TableCell>
                      <TableCell className="text-muted tabular-nums">{n.attempts}</TableCell>
                      <TableCell className="max-w-xs truncate text-xs text-error">{n.lastError || "\u2014"}</TableCell>
                      <TableCell className="text-muted">{formatDateTime(n.updatedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onPageChange={setPage} />
            </>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
