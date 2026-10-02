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
import { useCreateFlag, useUpdateFlag } from "@/hooks/use-feature-flags";
import { useAuthStore } from "@/store/auth-store";
import type { FlagEnvironment, FlagResponse, FlagType, TargetType } from "@/types/feature-flag";

const TYPES: FlagType[] = ["BOOLEAN", "INTEGER", "LONG", "DOUBLE", "STRING", "JSON"];
const ENVIRONMENTS: FlagEnvironment[] = ["GLOBAL", "DEVELOPMENT", "STAGING", "PRODUCTION"];
const TARGET_TYPES: TargetType[] = ["GLOBAL", "ENVIRONMENT", "USER", "ROLE", "TENANT"];

interface FormValues {
  key: string;
  name: string;
  description?: string;
  type: FlagType;
  environment: FlagEnvironment;
  targetType: TargetType;
  value: string;
  rolloutPercentage: string;
}

export function FlagFormDialog({ flag }: { flag?: FlagResponse }) {
  const [open, setOpen] = useState(false);
  const [valueError, setValueError] = useState<string | null>(null);
  const isEdit = !!flag;
  const actor = useAuthStore((s) => s.user?.email);

  const createFlag = useCreateFlag();
  const updateFlag = useUpdateFlag(flag?.key ?? "", flag?.environment ?? "GLOBAL");

  const form = useForm<FormValues>({
    defaultValues: {
      key: flag?.key ?? "",
      name: flag?.name ?? "",
      description: flag?.description ?? "",
      type: flag?.type ?? "BOOLEAN",
      environment: flag?.environment ?? "GLOBAL",
      targetType: flag?.targetType ?? "GLOBAL",
      value: flag ? (typeof flag.value === "string" ? flag.value : JSON.stringify(flag.value)) : "true",
      rolloutPercentage: flag ? String(flag.rolloutPercentage) : "100",
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({
        key: flag?.key ?? "",
        name: flag?.name ?? "",
        description: flag?.description ?? "",
        type: flag?.type ?? "BOOLEAN",
        environment: flag?.environment ?? "GLOBAL",
        targetType: flag?.targetType ?? "GLOBAL",
        value: flag ? (typeof flag.value === "string" ? flag.value : JSON.stringify(flag.value)) : "true",
        rolloutPercentage: flag ? String(flag.rolloutPercentage) : "100",
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  async function onSubmit(values: FormValues) {
    setValueError(null);
    let value: unknown = values.value;
    if (values.type === "JSON") {
      try {
        value = JSON.parse(values.value);
      } catch {
        setValueError("Must be valid JSON");
        return;
      }
    } else if (values.type === "BOOLEAN") {
      value = values.value === "true";
    } else if (["INTEGER", "LONG", "DOUBLE"].includes(values.type)) {
      value = Number(values.value);
    }

    if (isEdit && flag) {
      await updateFlag.mutateAsync({
        name: values.name,
        description: values.description,
        value,
        rolloutPercentage: Number(values.rolloutPercentage),
        actor,
      });
    } else {
      await createFlag.mutateAsync({
        key: values.key,
        name: values.name,
        description: values.description,
        type: values.type,
        environment: values.environment,
        targetType: values.targetType,
        value,
        rolloutPercentage: Number(values.rolloutPercentage),
        actor,
      });
    }
    setOpen(false);
  }

  const pending = createFlag.isPending || updateFlag.isPending;
  const type = form.watch("type");

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
            New Flag
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit flag" : "Create flag"}</DialogTitle>
          <DialogDescription>
            {isEdit ? "Key, type, and environment are immutable once created." : "Flags are identified by key + environment (defaults to GLOBAL)."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            {!isEdit && (
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label htmlFor="key">Key</Label>
                  <Input id="key" className="font-data" placeholder="e.g. new_checkout_flow" {...form.register("key", { required: true })} />
                </div>
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
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="name">Name</Label>
              <Input id="name" {...form.register("name", { required: true })} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="description">Description</Label>
              <Textarea id="description" rows={2} {...form.register("description")} />
            </div>
            {!isEdit && (
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label htmlFor="environment">Environment</Label>
                  <Controller
                    control={form.control}
                    name="environment"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger id="environment">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {ENVIRONMENTS.map((e) => (
                            <SelectItem key={e} value={e}>
                              {e}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="targetType">Target type</Label>
                  <Controller
                    control={form.control}
                    name="targetType"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger id="targetType">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {TARGET_TYPES.map((t) => (
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
            )}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="value">Value{type === "BOOLEAN" ? " (true/false)" : ""}</Label>
                <Input id="value" className="font-data" {...form.register("value", { required: true })} />
                {valueError && <p className="text-xs text-error">{valueError}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="rolloutPercentage">Rollout %</Label>
                <Input id="rolloutPercentage" type="number" min={0} max={100} {...form.register("rolloutPercentage")} />
              </div>
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={pending}>
              {isEdit ? "Save changes" : "Create flag"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
