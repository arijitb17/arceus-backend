"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Plus } from "lucide-react";
import { toast } from "sonner";
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
import { useCreateOrganization } from "@/hooks/use-organizations";
import { getErrorMessage } from "@/lib/api/error";

interface FormValues {
  name: string;
  slug: string;
  description: string;
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function CreateOrganizationDialog() {
  const [open, setOpen] = useState(false);
  const createOrganization = useCreateOrganization();

  const form = useForm<FormValues>({
    defaultValues: { name: "", slug: "", description: "" },
  });

  const nameValue = form.watch("name");
  const slugTouched = form.formState.dirtyFields.slug;

  useEffect(() => {
    if (!slugTouched) {
      form.setValue("slug", slugify(nameValue));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nameValue]);

  useEffect(() => {
    if (open) form.reset({ name: "", slug: "", description: "" });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  async function onSubmit(values: FormValues) {
    try {
      await createOrganization.mutateAsync({
        name: values.name,
        slug: values.slug,
        description: values.description || undefined,
      });
      toast.success("Organization created.");
      setOpen(false);
    } catch (error) {
      toast.error(getErrorMessage(error, "Couldn't create the organization."));
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus />
          New organization
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New organization</DialogTitle>
          <DialogDescription>
            Everything you build - projects, modules, keys - lives under an organization.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="name">Name</Label>
              <Input id="name" placeholder="Acme Inc" autoFocus {...form.register("name", { required: true })} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="slug">Slug</Label>
              <Input id="slug" placeholder="acme-inc" {...form.register("slug", { required: true })} />
              <p className="text-xs text-muted">Lowercase, alphanumeric, hyphen-separated. Used in the CLI and SDK config.</p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="description">Description (optional)</Label>
              <Textarea id="description" rows={2} {...form.register("description")} />
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={createOrganization.isPending}>
              Create organization
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
