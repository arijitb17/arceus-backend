"use client";

import { useState } from "react";
import { ScrollText } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { FilterBar } from "@/components/shared/filter-bar";
import { SearchInput } from "@/components/shared/search-input";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { StatusBadge } from "@/components/shared/status-badge";
import { SendNotificationDialog } from "@/features/notifications/send-notification-dialog";
import { useNotifications } from "@/hooks/use-notifications";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { NotificationChannel, NotificationStatus } from "@/types/notification";

const CHANNELS: NotificationChannel[] = ["EMAIL", "SMS", "PUSH", "SLACK", "DISCORD", "WEBHOOK"];
const STATUSES: NotificationStatus[] = ["PENDING", "SENT", "FAILED", "RETRYING", "DEAD", "SUPPRESSED"];

export default function NotificationHistoryPage() {
  const [to, setTo] = useState("");
  const [channel, setChannel] = useState<NotificationChannel | "ALL">("ALL");
  const [status, setStatus] = useState<NotificationStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const debouncedTo = useDebouncedValue(to);

  const { data, isLoading, isError, error, refetch } = useNotifications({
    to: debouncedTo || undefined,
    channel: channel === "ALL" ? undefined : channel,
    status: status === "ALL" ? undefined : status,
    page,
    size: 20,
  });

  return (
    <AppShell title="Notification History">
      <div className="space-y-6">
        <PageHeader
          title="History"
          description="Every notification the platform has attempted to send"
          actions={<SendNotificationDialog />}
        />

        <Card>
          <div className="flex flex-wrap items-center gap-3 border-b border-border p-3">
            <SearchInput
              value={to}
              onChange={(v) => {
                setTo(v);
                setPage(0);
              }}
              placeholder="Search recipient..."
              className="max-w-xs"
            />
            <FilterBar>
              <Select
                value={channel}
                onValueChange={(v) => {
                  setChannel(v as NotificationChannel | "ALL");
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-36">
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
              <Select
                value={status}
                onValueChange={(v) => {
                  setStatus(v as NotificationStatus | "ALL");
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-36">
                  <SelectValue placeholder="Status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All statuses</SelectItem>
                  {STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {s}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FilterBar>
          </div>

          {isLoading && (
            <CardContent className="space-y-2">
              {Array.from({ length: 6 }).map((_, i) => (
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
              <EmptyState icon={ScrollText} title="No notifications found" description="Try widening your filters, or send a test notification." />
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
                    <TableHead>Sent</TableHead>
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
                      <TableCell className="text-muted">{formatDateTime(n.sentAt ?? n.createdAt)}</TableCell>
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
