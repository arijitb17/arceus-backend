"use client";

import { useEffect, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
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
import { useCreateAlertRule, useUpdateAlertRule } from "@/hooks/use-monitoring";
import type { AlertRuleResponse, AlertSeverity, AlertType } from "@/types/monitoring";

const TYPES: AlertType[] = [
  "SERVICE_DOWN",
  "HIGH_CPU",
  "HIGH_MEMORY",
  "HIGH_ERROR_RATE",
  "HIGH_RESPONSE_TIME",
  "RABBITMQ_OFFLINE",
  "REDIS_OFFLINE",
  "DATABASE_OFFLINE",
];
const SEVERITIES: AlertSeverity[] = ["CRITICAL", "WARNING", "INFO"];

interface FormValues {
  name: string;
  type: AlertType;
  serviceName: string;
  thresholdValue: string;
  severity: AlertSeverity;
  enabled: boolean;
  description: string;
}

export function AlertRuleDialog({ rule }: { rule?: AlertRuleResponse }) {
  const [open, setOpen] = useState(false);
  const isEdit = !!rule;
  const createRule = useCreateAlertRule();
  const updateRule = useUpdateAlertRule(rule?.id ?? "");

  const form = useForm<FormValues>({
    defaultValues: {
      name: rule?.name ?? "",
      type: rule?.type ?? "SERVICE_DOWN",
      serviceName: rule?.serviceName ?? "",
      thresholdValue: rule ? String(rule.thresholdValue) : "0",
      severity: rule?.severity ?? "WARNING",
      enabled: rule?.enabled ?? true,
      description: rule?.description ?? "",
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({
        name: rule?.name ?? "",
        type: rule?.type ?? "SERVICE_DOWN",
        serviceName: rule?.serviceName ?? "",
        thresholdValue: rule ? String(rule.thresholdValue) : "0",
        severity: rule?.severity ?? "WARNING",
        enabled: rule?.enabled ?? true,
        description: rule?.description ?? "",
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  async function onSubmit(values: FormValues) {
    const body = {
      name: values.name,
      type: values.type,
      serviceName: values.serviceName || null,
      thresholdValue: Number(values.thresholdValue),
      severity: values.severity,
      enabled: values.enabled,
      description: values.description || null,
    };
    if (isEdit && rule) {
      await updateRule.mutateAsync(body);
    } else {
      await createRule.mutateAsync(body);
    }
    setOpen(false);
  }

  const pending = createRule.isPending || updateRule.isPending;

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
            New Alert Rule
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit alert rule" : "Create alert rule"}</DialogTitle>
          <DialogDescription>Triggers an alert event when the threshold is crossed for the given metric type.</DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="name">Name</Label>
              <Input id="name" {...form.register("name", { required: true })} />
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
                <Label htmlFor="severity">Severity</Label>
                <Controller
                  control={form.control}
                  name="severity"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="severity">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {SEVERITIES.map((s) => (
                          <SelectItem key={s} value={s}>
                            {s}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="serviceName">Service (optional)</Label>
                <Input id="serviceName" className="font-data" placeholder="Leave blank for platform-wide" {...form.register("serviceName")} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="thresholdValue">Threshold</Label>
                <Input id="thresholdValue" type="number" step="any" {...form.register("thresholdValue", { required: true })} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="description">Description</Label>
              <Textarea id="description" rows={2} {...form.register("description")} />
            </div>
            <div className="flex items-center justify-between rounded-md border border-border bg-surface px-3 py-2.5">
              <Label htmlFor="enabled" className="cursor-pointer">
                Enabled
              </Label>
              <Controller control={form.control} name="enabled" render={({ field }) => <Switch id="enabled" checked={field.value} onCheckedChange={field.onChange} />} />
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={pending}>
              {isEdit ? "Save changes" : "Create rule"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
