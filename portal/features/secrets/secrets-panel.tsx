"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { Lock, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useCreateSecret, useRevokeSecret, useSecrets } from "@/hooks/use-secrets";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { EnvironmentType } from "@/types/project";

interface FormValues {
  secretKey: string;
  value: string;
}

export function SecretsPanel({
  organizationId,
  projectId,
  environment,
}: {
  organizationId: string;
  projectId: string;
  environment: EnvironmentType;
}) {
  const { data: secrets, isLoading, isError, error, refetch } = useSecrets(organizationId, projectId, environment);
  const createSecret = useCreateSecret(organizationId, projectId, environment);
  const revokeSecret = useRevokeSecret(organizationId, projectId, environment);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null);
  const form = useForm<FormValues>({ defaultValues: { secretKey: "", value: "" } });

  async function onSubmit(values: FormValues) {
    try {
      await createSecret.mutateAsync({ secretKey: values.secretKey.toUpperCase(), value: values.value });
      toast.success("Secret created.");
      form.reset({ secretKey: "", value: "" });
      setDialogOpen(false);
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't create that secret."));
    }
  }

  async function onRevoke(secretKey: string) {
    try {
      await revokeSecret.mutateAsync(secretKey);
      toast.success("Secret revoked.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't revoke that secret."));
    } finally {
      setRevokeTarget(null);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogTrigger asChild>
            <Button size="sm">
              <Plus />
              New secret
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>New secret</DialogTitle>
              <DialogDescription>
                Encrypted at rest, scoped to <span className="font-medium text-foreground">{environment}</span>.
                Never shown again after creation.
              </DialogDescription>
            </DialogHeader>
            <form onSubmit={form.handleSubmit(onSubmit)}>
              <DialogBody className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="secret-key">Key</Label>
                  <Input
                    id="secret-key"
                    placeholder="STRIPE_SECRET_KEY"
                    className="font-mono uppercase"
                    autoFocus
                    {...form.register("secretKey", { required: true })}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="secret-value">Value</Label>
                  <Input id="secret-value" type="password" {...form.register("value", { required: true })} />
                </div>
              </DialogBody>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" loading={createSecret.isPending}>
                  Create
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10" />
          ))}
        </div>
      )}

      {isError && <ErrorState message={getErrorMessage(error)} onRetry={refetch} />}

      {secrets && secrets.length === 0 && (
        <EmptyState icon={Lock} title="No secrets yet" description="Secrets are injected into deploys and resolved server-side - never returned in plaintext." />
      )}

      {secrets && secrets.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Key</TableHead>
              <TableHead>Value</TableHead>
              <TableHead>Created</TableHead>
              <TableHead className="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {secrets.map((secret) => (
              <TableRow key={secret.id}>
                <TableCell className="font-mono text-xs">{secret.secretKey}</TableCell>
                <TableCell className="font-mono text-xs text-muted">{secret.maskedValue}</TableCell>
                <TableCell className="text-muted">{formatDateTime(secret.createdAt)}</TableCell>
                <TableCell>
                  <Button variant="ghost" size="icon" onClick={() => setRevokeTarget(secret.secretKey)}>
                    <Trash2 className="size-3.5" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <ConfirmDialog
        open={!!revokeTarget}
        onOpenChange={(open) => !open && setRevokeTarget(null)}
        title="Revoke this secret?"
        description="Deploys and module calls that reference it will fail to resolve it afterward."
        confirmLabel="Revoke"
        onConfirm={() => revokeTarget && onRevoke(revokeTarget)}
        loading={revokeSecret.isPending}
      />
    </div>
  );
}
