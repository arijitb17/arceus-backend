"use client";

import { useState } from "react";
import { FileText } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { FilterBar } from "@/components/shared/filter-bar";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { TemplateFormDialog } from "@/features/notifications/template-form-dialog";
import { useTemplates, useDeleteTemplate, useToggleTemplateStatus } from "@/hooks/use-notifications";
import { getErrorMessage } from "@/lib/api/error";
import type { NotificationChannel } from "@/types/notification";

const CHANNELS: NotificationChannel[] = ["EMAIL", "SMS", "PUSH", "SLACK", "DISCORD", "WEBHOOK"];

export default function TemplatesPage() {
  const [channel, setChannel] = useState<NotificationChannel | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error, refetch } = useTemplates({
    channel: channel === "ALL" ? undefined : channel,
    page,
    size: 20,
  });
  const deleteTemplate = useDeleteTemplate();
  const toggleStatus = useToggleTemplateStatus();
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; code: string } | null>(null);

  return (
    <AppShell title="Notification Templates">
      <div className="space-y-6">
        <PageHeader
          title="Templates"
          description="Reusable message templates, one per channel + code"
          actions={<TemplateFormDialog />}
        />

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
              <EmptyState icon={FileText} title="No templates found" />
            </CardContent>
          )}

          {data && data.items.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Code</TableHead>
                    <TableHead>Channel</TableHead>
                    <TableHead>Subject</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((t) => (
                    <TableRow key={t.id}>
                      <TableCell className="font-data">{t.code}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{t.channel}</Badge>
                      </TableCell>
                      <TableCell className="text-muted">{t.subject || "\u2014"}</TableCell>
                      <TableCell>
                        <button
                          onClick={() => toggleStatus.mutate({ id: t.id, active: !t.active })}
                          disabled={toggleStatus.isPending}
                        >
                          <Badge variant={t.active ? "success" : "default"} dot className="cursor-pointer">
                            {t.active ? "Active" : "Inactive"}
                          </Badge>
                        </button>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <TemplateFormDialog template={t} />
                          <Button variant="ghost" size="sm" onClick={() => setDeleteTarget({ id: t.id, code: t.code })}>
                            Delete
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onPageChange={setPage} />
            </>
          )}
        </Card>
      </div>

      <ConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title={`Delete "${deleteTarget?.code}"?`}
        description="This can't be undone. Any notification still referencing this template code will fail to send."
        loading={deleteTemplate.isPending}
        onConfirm={() => {
          if (deleteTarget) deleteTemplate.mutate(deleteTarget.id, { onSuccess: () => setDeleteTarget(null) });
        }}
      />
    </AppShell>
  );
}
