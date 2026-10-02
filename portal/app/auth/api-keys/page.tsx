"use client";

import { useState } from "react";
import { KeyRound, Copy } from "lucide-react";
import { toast } from "sonner";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { ApiKeyCreateDialog } from "@/features/auth/api-key-create-dialog";
import { useApiKeys, useRevealApiKey, useRevokeApiKey } from "@/hooks/use-api-keys";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";

export default function ApiKeysPage() {
  const { data, isLoading, isError, error, refetch } = useApiKeys();
  const revokeApiKey = useRevokeApiKey();
  const revealApiKey = useRevealApiKey();
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null);
  const [revealingId, setRevealingId] = useState<string | null>(null);

  function copyKey(key: string) {
    navigator.clipboard.writeText(key);
    toast.success("Copied to clipboard.");
  }

  async function copyFullKey(id: string) {
    setRevealingId(id);
    try {
      const rawKey = await revealApiKey.mutateAsync(id);
      copyKey(rawKey);
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't retrieve that key."));
    } finally {
      setRevealingId(null);
    }
  }

  return (
    <AppShell title="API Keys">
      <div className="space-y-6">
        <PageHeader
          title="API Keys"
          description="Personal keys for programmatic access to the Arceus API"
          actions={<ApiKeyCreateDialog />}
        />

        <Card>
          {isLoading && (
            <CardContent className="space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-10" />
              ))}
            </CardContent>
          )}

          {isError && (
            <CardContent>
              <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />
            </CardContent>
          )}

          {data && data.length === 0 && (
            <CardContent>
              <EmptyState icon={KeyRound} title="No API keys yet" description="Create one to authenticate programmatic requests." />
            </CardContent>
          )}

          {data && data.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Key</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last used</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.map((key) => (
                  <TableRow key={key.id}>
                    <TableCell className="font-medium">{key.name}</TableCell>
                    <TableCell className="font-data text-muted">
                      <button
                        type="button"
                        disabled={revealingId === key.id}
                        onClick={() => copyFullKey(key.id)}
                        className="inline-flex items-center gap-1.5 hover:text-foreground transition-colors disabled:opacity-50"
                        title="Copy the full key - decrypted on demand"
                      >
                        {key.keyPrefix}
                        {"\u2026"}
                        <Copy className="size-3" />
                      </button>
                    </TableCell>
                    <TableCell>
                      <Badge variant={key.revoked ? "error" : "success"} dot>
                        {key.revoked ? "Revoked" : "Active"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted">{key.lastUsedAt ? formatDateTime(key.lastUsedAt) : "Never"}</TableCell>
                    <TableCell className="text-muted">{formatDateTime(key.createdAt)}</TableCell>
                    <TableCell className="text-right">
                      {!key.revoked && (
                        <Button variant="ghost" size="sm" onClick={() => setRevokeTarget(key.id)}>
                          Revoke
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Card>
      </div>

      <ConfirmDialog
        open={!!revokeTarget}
        onOpenChange={(open) => !open && setRevokeTarget(null)}
        title="Revoke this API key?"
        description="Any application using this key will immediately lose access. This can't be undone."
        confirmLabel="Revoke key"
        loading={revokeApiKey.isPending}
        onConfirm={() => {
          if (revokeTarget) revokeApiKey.mutate(revokeTarget, { onSuccess: () => setRevokeTarget(null) });
        }}
      />
    </AppShell>
  );
}
