"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Copy, KeyRound, Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { Alert, AlertDescription } from "@/components/ui/alert";
import { useCreateApiKey } from "@/hooks/use-api-keys";

const schema = z.object({ name: z.string().min(1, "Name is required").max(100) });

export function ApiKeyCreateDialog() {
  const [open, setOpen] = useState(false);
  const [createdKey, setCreatedKey] = useState<string | null>(null);
  const createApiKey = useCreateApiKey();

  const form = useForm<{ name: string }>({
    resolver: zodResolver(schema),
    defaultValues: { name: "" },
  });

  async function onSubmit(values: { name: string }) {
    const result = await createApiKey.mutateAsync(values);
    setCreatedKey(result.key);
  }

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      setCreatedKey(null);
      form.reset();
    }
  }

  function copyKey() {
    if (createdKey) {
      navigator.clipboard.writeText(createdKey);
      toast.success("Copied to clipboard.");
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button>
          <Plus />
          New API Key
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{createdKey ? "API key created" : "Create API key"}</DialogTitle>
          <DialogDescription>
            {createdKey
              ? "Copy it now, or click its prefix in the list anytime later to copy it again."
              : "Give this key a name so you can recognize it later."}
          </DialogDescription>
        </DialogHeader>

        {createdKey ? (
          <>
            <DialogBody>
              <Alert variant="warning">
                <KeyRound />
                <AlertDescription>Store this somewhere safe - treat it like a password.</AlertDescription>
              </Alert>
              <div className="mt-3 flex items-start gap-2 rounded-md border border-border bg-surface px-3 py-2.5">
                <code className="flex-1 break-all font-data text-foreground">{createdKey}</code>
                <Button variant="ghost" size="icon" onClick={copyKey} className="shrink-0">
                  <Copy className="size-3.5" />
                </Button>
              </div>
            </DialogBody>
            <DialogFooter>
              <Button onClick={() => handleOpenChange(false)}>Done</Button>
            </DialogFooter>
          </>
        ) : (
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <DialogBody>
              <div className="space-y-1.5">
                <Label htmlFor="key-name">Name</Label>
                <Input id="key-name" placeholder="e.g. CI pipeline" {...form.register("name")} autoFocus />
                {form.formState.errors.name && (
                  <p className="text-xs text-error">{form.formState.errors.name.message}</p>
                )}
              </div>
            </DialogBody>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={createApiKey.isPending}>
                Create key
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
