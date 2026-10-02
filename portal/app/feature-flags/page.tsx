"use client";

import { useState } from "react";
import { Flag } from "lucide-react";
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
import { FlagFormDialog } from "@/features/feature-flags/flag-form-dialog";
import { FlagHistoryDialog } from "@/features/feature-flags/flag-history-dialog";
import { useFlags, useToggleFlag, useDeleteFlag } from "@/hooks/use-feature-flags";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { FlagEnvironment, FlagStatus } from "@/types/feature-flag";

const ENVIRONMENTS: FlagEnvironment[] = ["GLOBAL", "DEVELOPMENT", "STAGING", "PRODUCTION"];
const STATUSES: FlagStatus[] = ["ENABLED", "DISABLED"];

export default function FeatureFlagsPage() {
  const [environment, setEnvironment] = useState<FlagEnvironment | "ALL">("ALL");
  const [status, setStatus] = useState<FlagStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, error, refetch } = useFlags({
    environment: environment === "ALL" ? undefined : environment,
    status: status === "ALL" ? undefined : status,
    page,
    size: 20,
  });

  const toggleFlag = useToggleFlag();
  const deleteFlag = useDeleteFlag();
  const [deleteTarget, setDeleteTarget] = useState<{ key: string; environment: FlagEnvironment } | null>(null);

  return (
    <AppShell title="Feature Flags">
      <div className="space-y-6">
        <PageHeader title="Feature Flags" description="Runtime toggles, targeted by environment, user, role, or tenant" actions={<FlagFormDialog />} />

        <Card>
          <div className="flex items-center gap-3 border-b border-border p-3">
            <FilterBar>
              <Select
                value={environment}
                onValueChange={(v) => {
                  setEnvironment(v as FlagEnvironment | "ALL");
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-40">
                  <SelectValue placeholder="Environment" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All environments</SelectItem>
                  {ENVIRONMENTS.map((e) => (
                    <SelectItem key={e} value={e}>
                      {e}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={status}
                onValueChange={(v) => {
                  setStatus(v as FlagStatus | "ALL");
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
              <EmptyState icon={Flag} title="No flags found" description="Try widening your filters, or create the first one." />
            </CardContent>
          )}

          {data && data.items.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Key</TableHead>
                    <TableHead>Environment</TableHead>
                    <TableHead>Target</TableHead>
                    <TableHead>Rollout</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Updated</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((f) => (
                    <TableRow key={f.id}>
                      <TableCell>
                        <div className="flex flex-col">
                          <span className="font-data">{f.key}</span>
                          <span className="text-xs text-muted">{f.name}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{f.environment}</Badge>
                      </TableCell>
                      <TableCell className="text-muted">{f.targetType}</TableCell>
                      <TableCell className="text-muted tabular-nums">{f.rolloutPercentage}%</TableCell>
                      <TableCell>
                        <button
                          onClick={() => toggleFlag.mutate({ key: f.key, environment: f.environment, enabled: f.status !== "ENABLED" })}
                          disabled={toggleFlag.isPending}
                        >
                          <Badge variant={f.status === "ENABLED" ? "success" : "default"} dot className="cursor-pointer">
                            {f.status}
                          </Badge>
                        </button>
                      </TableCell>
                      <TableCell className="text-muted">{formatDateTime(f.updatedAt)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <FlagHistoryDialog flagKey={f.key} />
                          <FlagFormDialog flag={f} />
                          <Button variant="ghost" size="sm" onClick={() => setDeleteTarget({ key: f.key, environment: f.environment })}>
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
        title={`Delete "${deleteTarget?.key}"?`}
        description="This can't be undone. Any service still evaluating this flag will fall back to its default behavior."
        loading={deleteFlag.isPending}
        onConfirm={() => {
          if (deleteTarget) deleteFlag.mutate(deleteTarget, { onSuccess: () => setDeleteTarget(null) });
        }}
      />
    </AppShell>
  );
}
