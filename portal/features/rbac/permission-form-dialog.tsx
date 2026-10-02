"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import { useCreatePermission, useUpdatePermission } from "@/hooks/use-rbac";
import type { PermissionResponse } from "@/types/rbac";

const createSchema = z.object({
  code: z.string().regex(/^[a-z0-9_-]+:[a-z0-9_-]+$/, "Format: resource:action, e.g. users:write"),
  category: z.string().min(1, "Category is required"),
  description: z.string().optional(),
});
const editSchema = z.object({
  category: z.string().min(1, "Category is required"),
  description: z.string().optional(),
});

export function PermissionFormDialog({ permission }: { permission?: PermissionResponse }) {
  const [open, setOpen] = useState(false);
  const isEdit = !!permission;

  const createPermission = useCreatePermission();
  const updatePermission = useUpdatePermission(permission?.id ?? "");

  const form = useForm<{ code?: string; category: string; description?: string }>({
    resolver: zodResolver(isEdit ? editSchema : createSchema),
    defaultValues: {
      code: permission?.code ?? "",
      category: permission?.category ?? "",
      description: permission?.description ?? "",
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({ code: permission?.code ?? "", category: permission?.category ?? "", description: permission?.description ?? "" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  async function onSubmit(values: { code?: string; category: string; description?: string }) {
    if (isEdit && permission) {
      await updatePermission.mutateAsync({ category: values.category, description: values.description });
    } else {
      await createPermission.mutateAsync({ code: values.code!, category: values.category, description: values.description });
    }
    setOpen(false);
  }

  const pending = createPermission.isPending || updatePermission.isPending;

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        {isEdit ? (
          <Button variant="ghost" size="sm">Edit</Button>
        ) : (
          <Button>
            <Plus />
            New Permission
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit permission" : "Create permission"}</DialogTitle>
          <DialogDescription>
            {isEdit ? "Code is immutable once created." : "Format: resource:action, e.g. cache:write"}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            {!isEdit && (
              <div className="space-y-1.5">
                <Label htmlFor="code">Code</Label>
                <Input id="code" placeholder="e.g. users:write" className="font-data" {...form.register("code")} />
                {form.formState.errors.code && <p className="text-xs text-error">{form.formState.errors.code.message}</p>}
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="category">Category</Label>
              <Input id="category" placeholder="e.g. Users" {...form.register("category")} />
              {form.formState.errors.category && <p className="text-xs text-error">{form.formState.errors.category.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="description">Description</Label>
              <Textarea id="description" rows={3} {...form.register("description")} />
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={pending}>
              {isEdit ? "Save changes" : "Create permission"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
