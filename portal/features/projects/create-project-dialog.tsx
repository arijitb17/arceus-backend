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
import { useCreateProject } from "@/hooks/use-projects";
import { getErrorMessage } from "@/lib/api/error";

interface FormValues {
  name: string;
  slug: string;
  description: string;
}

function slugify(value: string) {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

export function CreateProjectDialog({ organizationId }: { organizationId: string }) {
  const [open, setOpen] = useState(false);
  const createProject = useCreateProject(organizationId);

  const form = useForm<FormValues>({ defaultValues: { name: "", slug: "", description: "" } });
  const nameValue = form.watch("name");
  const slugTouched = form.formState.dirtyFields.slug;

  useEffect(() => {
    if (!slugTouched) form.setValue("slug", slugify(nameValue));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nameValue]);

  useEffect(() => {
    if (open) form.reset({ name: "", slug: "", description: "" });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  async function onSubmit(values: FormValues) {
    try {
      await createProject.mutateAsync({
        name: values.name,
        slug: values.slug,
        description: values.description || undefined,
      });
      toast.success("Project created - Development, Staging, and Production environments are ready.");
      setOpen(false);
    } catch (error) {
      toast.error(getErrorMessage(error, "Couldn't create the project."));
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus />
          New project
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New project</DialogTitle>
          <DialogDescription>
            Creates Development, Staging, and Production environments automatically - nothing to configure up front.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="p-name">Name</Label>
              <Input id="p-name" placeholder="Storefront" autoFocus {...form.register("name", { required: true })} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="p-slug">Slug</Label>
              <Input id="p-slug" placeholder="storefront" {...form.register("slug", { required: true })} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="p-description">Description (optional)</Label>
              <Textarea id="p-description" rows={2} {...form.register("description")} />
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={createProject.isPending}>
              Create project
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
