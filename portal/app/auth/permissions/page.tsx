"use client";

import { useState } from "react";
import { ListTree } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { SearchInput } from "@/components/shared/search-input";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { PermissionFormDialog } from "@/features/rbac/permission-form-dialog";
import { usePermissions, useDeletePermission } from "@/hooks/use-rbac";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { getErrorMessage } from "@/lib/api/error";

export default function PermissionsPage() {
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(0);
  const debouncedCategory = useDebouncedValue(category);
  const { data, isLoading, isError, error, refetch } = usePermissions({ category: debouncedCategory || undefined, page, size: 20 });
  const deletePermission = useDeletePermission();
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; code: string } | null>(null);

  return (
    <AppShell title="Permissions">
      <div className="space-y-6">
        <PageHeader
          title="Permissions"
          description="The permission catalog roles are built from"
          actions={<PermissionFormDialog />}
        />

        <Card>
          <div className="flex items-center gap-3 border-b border-border p-3">
            <SearchInput
              value={category}
              onChange={(v) => {
                setCategory(v);
                setPage(0);
              }}
              placeholder="Filter by category..."
              className="max-w-xs"
            />
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

          {data && data.content.length === 0 && (
            <CardContent>
              <EmptyState icon={ListTree} title="No permissions found" />
            </CardContent>
          )}

          {data && data.content.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Code</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell className="font-data">{p.code}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{p.category}</Badge>
                      </TableCell>
                      <TableCell className="text-muted">{p.description || "\u2014"}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <PermissionFormDialog permission={p} />
                          <Button variant="ghost" size="sm" onClick={() => setDeleteTarget({ id: p.id, code: p.code })}>
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
        description="This also unassigns it from every role that currently has it. This can't be undone."
        loading={deletePermission.isPending}
        onConfirm={() => {
          if (deleteTarget) deletePermission.mutate(deleteTarget.id, { onSuccess: () => setDeleteTarget(null) });
        }}
      />
    </AppShell>
  );
}
