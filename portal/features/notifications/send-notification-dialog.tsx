"use client";

import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Send } from "lucide-react";
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
import { useSendNotification } from "@/hooks/use-notifications";
import type { NotificationChannel } from "@/types/notification";

const CHANNELS: NotificationChannel[] = ["EMAIL", "SMS", "PUSH", "SLACK", "DISCORD", "WEBHOOK"];

const schema = z.object({
  channel: z.enum(["EMAIL", "SMS", "PUSH", "SLACK", "DISCORD", "WEBHOOK"]),
  template: z.string().min(1, "Template code is required"),
  to: z.string().min(1, "Recipient is required"),
  variables: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

export function SendNotificationDialog() {
  const [open, setOpen] = useState(false);
  const [variablesError, setVariablesError] = useState<string | null>(null);
  const sendNotification = useSendNotification();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { channel: "EMAIL", template: "", to: "", variables: "" },
  });

  async function onSubmit(values: FormValues) {
    setVariablesError(null);
    let variables: Record<string, unknown> | undefined;
    if (values.variables?.trim()) {
      try {
        variables = JSON.parse(values.variables);
      } catch {
        setVariablesError("Must be valid JSON, e.g. { \"name\": \"Ada\" }");
        return;
      }
    }
    await sendNotification.mutateAsync({ channel: values.channel, template: values.template, to: values.to, variables });
    setOpen(false);
    form.reset();
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Send />
          Send Notification
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Send notification</DialogTitle>
          <DialogDescription>Queues a notification for delivery through an existing template.</DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
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
                <Label htmlFor="template">Template code</Label>
                <Input id="template" className="font-data" placeholder="e.g. welcome_email" {...form.register("template")} />
                {form.formState.errors.template && <p className="text-xs text-error">{form.formState.errors.template.message}</p>}
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="to">Recipient</Label>
              <Input id="to" placeholder="Email, phone, or webhook target" {...form.register("to")} />
              {form.formState.errors.to && <p className="text-xs text-error">{form.formState.errors.to.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="variables">Variables (JSON)</Label>
              <Textarea id="variables" rows={4} className="font-data" placeholder='{ "name": "Ada" }' {...form.register("variables")} />
              {variablesError && <p className="text-xs text-error">{variablesError}</p>}
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={sendNotification.isPending}>
              Send
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
