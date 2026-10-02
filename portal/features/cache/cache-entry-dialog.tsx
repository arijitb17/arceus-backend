"use client";

import { useEffect, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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
import { useSetCacheEntry } from "@/hooks/use-cache";
import type { CacheDataType, CacheResponse, TtlPolicy } from "@/types/cache";

const TYPES: CacheDataType[] = ["STRING", "JSON", "NUMBER", "HASH", "LIST", "SET"];
const TTL_POLICIES: TtlPolicy[] = ["NO_EXPIRY", "SLIDING", "ABSOLUTE", "CUSTOM"];

interface FormValues {
  namespace: string;
  key: string;
  type: CacheDataType;
  ttlPolicy: TtlPolicy;
  ttlSeconds?: string;
  value: string;
}

export function CacheEntryDialog({ existing }: { existing?: CacheResponse }) {
  const [open, setOpen] = useState(false);
  const [jsonError, setJsonError] = useState<string | null>(null);
  const isEdit = !!existing;
  const setEntry = useSetCacheEntry();

  const form = useForm<FormValues>({
    defaultValues: {
      namespace: existing?.namespace ?? "",
      key: existing?.key ?? "",
      type: existing?.type ?? "JSON",
      ttlPolicy: existing?.ttlPolicy ?? "NO_EXPIRY",
      ttlSeconds: "",
      value: existing ? JSON.stringify(existing.value, null, 2) : "",
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({
        namespace: existing?.namespace ?? "",
        key: existing?.key ?? "",
        type: existing?.type ?? "JSON",
        ttlPolicy: existing?.ttlPolicy ?? "NO_EXPIRY",
        ttlSeconds: "",
        value: existing ? JSON.stringify(existing.value, null, 2) : "",
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  async function onSubmit(values: FormValues) {
    setJsonError(null);
    let value: unknown = values.value;
    if (values.type === "JSON" || values.type === "NUMBER") {
      try {
        value = JSON.parse(values.value);
      } catch {
        setJsonError(values.type === "JSON" ? "Must be valid JSON" : "Must be a valid number");
        return;
      }
    }
    await setEntry.mutateAsync({
      namespace: values.namespace,
      key: values.key,
      value,
      type: values.type,
      ttlPolicy: values.ttlPolicy,
      ttlSeconds: values.ttlSeconds ? Number(values.ttlSeconds) : undefined,
    });
    setOpen(false);
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        {isEdit ? (
          <Button variant="ghost" size="sm">
            Edit
          </Button>
        ) : (
          <Button>
            <Plus />
            Set Entry
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit cache entry" : "Set cache entry"}</DialogTitle>
          <DialogDescription>Writes directly to Redis via cache-service. Existing values at the same namespace + key are overwritten.</DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="namespace">Namespace</Label>
                <Input id="namespace" className="font-data" disabled={isEdit} {...form.register("namespace", { required: true })} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="key">Key</Label>
                <Input id="key" className="font-data" disabled={isEdit} {...form.register("key", { required: true })} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="type">Type</Label>
                <Controller
                  control={form.control}
                  name="type"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="type">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {TYPES.map((t) => (
                          <SelectItem key={t} value={t}>
                            {t}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="ttlPolicy">TTL policy</Label>
                <Controller
                  control={form.control}
                  name="ttlPolicy"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="ttlPolicy">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {TTL_POLICIES.map((t) => (
                          <SelectItem key={t} value={t}>
                            {t}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>
            {form.watch("ttlPolicy") !== "NO_EXPIRY" && (
              <div className="space-y-1.5">
                <Label htmlFor="ttlSeconds">TTL seconds</Label>
                <Input id="ttlSeconds" type="number" className="w-40 font-data" {...form.register("ttlSeconds")} />
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="value">Value {form.watch("type") === "JSON" ? "(JSON)" : ""}</Label>
              <Textarea id="value" rows={6} className="font-data" {...form.register("value", { required: true })} />
              {jsonError && <p className="text-xs text-error">{jsonError}</p>}
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={setEntry.isPending}>
              {isEdit ? "Save changes" : "Set entry"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
