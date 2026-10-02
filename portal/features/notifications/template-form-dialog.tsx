"use client";

import { useEffect, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
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
import { useCreateTemplate, useUpdateTemplate } from "@/hooks/use-notifications";
import type { NotificationChannel, TemplateResponse } from "@/types/notification";

const CHANNELS: NotificationChannel[] = ["EMAIL", "SMS", "PUSH", "SLACK", "DISCORD", "WEBHOOK"];

const createSchema = z.object({
  channel: z.enum(["EMAIL", "SMS", "PUSH", "SLACK", "DISCORD", "WEBHOOK"]),
  code: z.string().min(1, "Code is required").max(100),
  subject: z.string().optional(),
  body: z.string().min(1, "Body is required"),
});
const editSchema = z.object({
  subject: z.string().optional(),
  body: z.string().min(1, "Body is required"),
});

type FormValues = { channel?: NotificationChannel; code?: string; subject?: string; body: string };

export function TemplateFormDialog({ template }: { template?: TemplateResponse }) {
  const [open, setOpen] = useState(false);
  const isEdit = !!template;

  const createTemplate = useCreateTemplate();
  const updateTemplate = useUpdateTemplate(template?.id ?? "");

  const form = useForm<FormValues>({
    resolver: zodResolver(isEdit ? editSchema : createSchema),
    defaultValues: {
      channel: template?.channel ?? "EMAIL",
      code: template?.code ?? "",
      subject: template?.subject ?? "",
      body: template?.body ?? "",
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({
        channel: template?.channel ?? "EMAIL",
        code: template?.code ?? "",
        subject: template?.subject ?? "",
        body: template?.body ?? "",
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  async function onSubmit(values: FormValues) {
    if (isEdit && template) {
      await updateTemplate.mutateAsync({ subject: values.subject, body: values.body });
    } else {
      await createTemplate.mutateAsync({
        channel: values.channel!,
        code: values.code!,
        subject: values.subject,
        body: values.body,
      });
    }
    setOpen(false);
  }

  const pending = createTemplate.isPending || updateTemplate.isPending;

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
            New Template
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit template" : "Create template"}</DialogTitle>
          <DialogDescription>
            {isEdit ? "Channel and code are immutable once created." : "Code identifies this template when sending a notification."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            {!isEdit && (
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label htmlFor="channel">Channel</Label>
                  <Controller
                    control={form.control}
                    name="channel"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger id="channel">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {CHANNELS.map((c) => (
                            <SelectItem key={c} value={c}>
                              {c}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="code">Code</Label>
                  <Input id="code" placeholder="e.g. welcome_email" className="font-data" {...form.register("code")} />
                  {form.formState.errors.code && <p className="text-xs text-error">{form.formState.errors.code.message}</p>}
                </div>
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="subject">Subject</Label>
              <Input id="subject" placeholder="Optional - used by EMAIL templates" {...form.register("subject")} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="body">Body</Label>
              <Textarea id="body" rows={6} className="font-data" placeholder="Use {{variableName}} for placeholders" {...form.register("body")} />
              {form.formState.errors.body && <p className="text-xs text-error">{form.formState.errors.body.message}</p>}
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={pending}>
              {isEdit ? "Save changes" : "Create template"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
