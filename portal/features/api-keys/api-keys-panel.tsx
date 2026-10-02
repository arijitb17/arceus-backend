"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { Copy, KeyRound, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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
import { useCreateProjectApiKey, useProjectApiKeys, useRevealProjectApiKey, useRevokeProjectApiKey } from "@/hooks/use-project-api-keys";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { ProjectApiKeyType } from "@/types/project-api-key";
import type { EnvironmentType } from "@/types/project";

interface FormValues {
  name: string;
  keyType: ProjectApiKeyType;
}

export function ApiKeysPanel({
  organizationId,
  projectId,
  environment,
}: {
  organizationId: string;
  projectId: string;
  environment: EnvironmentType;
}) {
  const { data: keys, isLoading, isError, error, refetch } = useProjectApiKeys(organizationId, projectId, environment);
  const createKey = useCreateProjectApiKey(organizationId, projectId, environment);
  const revokeKey = useRevokeProjectApiKey(organizationId, projectId, environment);
  const revealKey = useRevealProjectApiKey(organizationId, projectId);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null);
  const [justCreatedKey, setJustCreatedKey] = useState<string | null>(null);
  const [revealingId, setRevealingId] = useState<string | null>(null);

  const form = useForm<FormValues>({ defaultValues: { name: "", keyType: "PUBLIC" } });

  async function onSubmit(values: FormValues) {
    try {
      const created = await createKey.mutateAsync({ name: values.name, keyType: values.keyType, environment });
      setJustCreatedKey(created.key);
      form.reset({ name: "", keyType: "PUBLIC" });
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't generate the key."));
    }
  }

  function copyKey(key: string) {
    navigator.clipboard.writeText(key);
    toast.success("Copied to clipboard.");
  }

  async function copyFullKey(id: string) {
    setRevealingId(id);
    try {
      const rawKey = await revealKey.mutateAsync(id);
      copyKey(rawKey);
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't retrieve that key."));
    } finally {
      setRevealingId(null);
    }
  }

  async function onRevoke(id: string) {
    try {
      await revokeKey.mutateAsync(id);
      toast.success("Key revoked.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't revoke that key."));
    } finally {
      setRevokeTarget(null);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Dialog
          open={dialogOpen}
          onOpenChange={(open) => {
            setDialogOpen(open);
            if (!open) setJustCreatedKey(null);
          }}
        >
          <DialogTrigger asChild>
            <Button size="sm">
              <Plus />
              Generate key
            </Button>
          </DialogTrigger>
          <DialogContent>
            {justCreatedKey ? (
              <>
                <DialogHeader>
                  <DialogTitle>Key generated</DialogTitle>
                  <DialogDescription>
                    Copy it now, or click its prefix in the list anytime later to copy it again.
                  </DialogDescription>
                </DialogHeader>
                <DialogBody className="space-y-3">
                  <Alert>
                    <AlertDescription className="font-mono text-xs break-all">{justCreatedKey}</AlertDescription>
                  </Alert>
                  <Button variant="outline" className="w-full" onClick={() => copyKey(justCreatedKey)}>
                    <Copy />
                    Copy key
                  </Button>
                </DialogBody>
                <DialogFooter>
                  <Button onClick={() => setDialogOpen(false)}>Done</Button>
                </DialogFooter>
              </>
            ) : (
              <>
                <DialogHeader>
                  <DialogTitle>Generate API key</DialogTitle>
                  <DialogDescription>
                    Scoped to <span className="font-medium text-foreground">{environment}</span>. PUBLIC keys are safe
                    for client-side SDKs; SECRET keys are for server-side use only.
                  </DialogDescription>
                </DialogHeader>
                <form onSubmit={form.handleSubmit(onSubmit)}>
                  <DialogBody className="space-y-4">
                    <div className="space-y-1.5">
                      <Label htmlFor="key-name">Name</Label>
                      <Input id="key-name" placeholder="Web app" autoFocus {...form.register("name", { required: true })} />
                    </div>
                    <div className="space-y-1.5">
                      <Label>Type</Label>
                      <Select
                        value={form.watch("keyType")}
                        onValueChange={(v) => form.setValue("keyType", v as ProjectApiKeyType)}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="PUBLIC">Public (client-side)</SelectItem>
                          <SelectItem value="SECRET">Secret (server-side)</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </DialogBody>
                  <DialogFooter>
                    <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                      Cancel
                    </Button>
                    <Button type="submit" loading={createKey.isPending}>
                      Generate
                    </Button>
                  </DialogFooter>
                </form>
              </>
            )}
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

      {keys && keys.length === 0 && (
        <EmptyState icon={KeyRound} title="No keys yet" description="Generate a key to authenticate your SDK." />
      )}

      {keys && keys.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Key</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Last used</TableHead>
              <TableHead className="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {keys.map((key) => (
              <TableRow key={key.id}>
                <TableCell>{key.name}</TableCell>
                <TableCell className="font-mono text-xs text-muted">
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
                  <Badge variant={key.keyType === "SECRET" ? "warning" : "primary"}>{key.keyType}</Badge>
                </TableCell>
                <TableCell>
                  <Badge variant={key.revoked ? "error" : "success"} dot>
                    {key.revoked ? "Revoked" : "Active"}
                  </Badge>
                </TableCell>
                <TableCell className="text-muted">{key.lastUsedAt ? formatDateTime(key.lastUsedAt) : "Never"}</TableCell>
                <TableCell>
                  {!key.revoked && (
                    <Button variant="ghost" size="icon" onClick={() => setRevokeTarget(key.id)}>
                      <Trash2 className="size-3.5" />
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <ConfirmDialog
        open={!!revokeTarget}
        onOpenChange={(open) => !open && setRevokeTarget(null)}
        title="Revoke this key?"
        description="Any client using this key will immediately lose access."
        confirmLabel="Revoke"
        onConfirm={() => revokeTarget && onRevoke(revokeTarget)}
        loading={revokeKey.isPending}
      />
    </div>
  );
}
