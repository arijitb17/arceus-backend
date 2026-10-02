"use client";

import { Fingerprint, BellRing, Database, Clock, Flag, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/shared/empty-error-state";
import { useModules, useEnableModule, useDisableModule } from "@/hooks/use-modules";
import { getErrorMessage } from "@/lib/api/error";
import { MODULE_LABELS, MODULE_TYPES, type ModuleStatus, type ModuleType } from "@/types/module";
import type { EnvironmentType } from "@/types/project";

const MODULE_ICONS: Record<ModuleType, typeof Fingerprint> = {
  AUTHENTICATION: Fingerprint,
  NOTIFICATION: BellRing,
  CACHE: Database,
  SCHEDULER: Clock,
  FEATURE_FLAGS: Flag,
};

const MODULE_DESCRIPTIONS: Record<ModuleType, string> = {
  AUTHENTICATION: "User sign-up, sign-in, and session management.",
  NOTIFICATION: "Email, SMS, and push notifications.",
  CACHE: "A namespaced key-value cache for this project.",
  SCHEDULER: "Cron and one-off background jobs.",
  FEATURE_FLAGS: "Runtime toggles for this project.",
};

function statusColor(status: ModuleStatus | undefined) {
  switch (status) {
    case "ACTIVE":
      return "success" as const;
    case "FAILED":
      return "error" as const;
    case "PENDING":
    case "PROVISIONING":
    case "DISABLING":
      return "warning" as const;
    default:
      return "default" as const;
  }
}

export function ModulesPanel({
  organizationId,
  projectId,
  environment,
}: {
  organizationId: string;
  projectId: string;
  environment: EnvironmentType;
}) {
  const { data: modules, isLoading, isError, error, refetch } = useModules(organizationId, projectId, environment);
  const enableModule = useEnableModule(organizationId, projectId, environment);
  const disableModule = useDisableModule(organizationId, projectId, environment);

  const statusByModule = new Map((modules ?? []).map((m) => [m.module, m]));

  async function onToggle(module: ModuleType, currentlyOn: boolean) {
    try {
      if (currentlyOn) {
        await disableModule.mutateAsync(module);
        toast.success(`Disabling ${MODULE_LABELS[module]}\u2026`);
      } else {
        await enableModule.mutateAsync(module);
        toast.success(`Enabling ${MODULE_LABELS[module]}\u2026 this provisions automatically, no config needed.`);
      }
    } catch (err) {
      toast.error(getErrorMessage(err, `Couldn't update ${MODULE_LABELS[module]}.`));
    }
  }

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-24" />
        ))}
      </div>
    );
  }

  if (isError) return <ErrorState message={getErrorMessage(error)} onRetry={refetch} />;

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {MODULE_TYPES.map((module) => {
        const Icon = MODULE_ICONS[module];
        const enablement = statusByModule.get(module);
        const status = enablement?.status;
        const isOn = status === "ACTIVE" || status === "PENDING" || status === "PROVISIONING";
        const busy = status === "PENDING" || status === "PROVISIONING" || status === "DISABLING";

        return (
          <Card key={module}>
            <CardContent className="flex items-start gap-3 p-4">
              <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary/15 text-primary">
                <Icon className="size-4" />
              </div>
              <div className="min-w-0 flex-1 space-y-1.5">
                <div className="flex items-center justify-between gap-2">
                  <h3 className="text-sm font-medium text-foreground">{MODULE_LABELS[module]}</h3>
                  <Switch
                    checked={isOn}
                    disabled={busy || enableModule.isPending || disableModule.isPending}
                    onCheckedChange={() => onToggle(module, isOn)}
                  />
                </div>
                <p className="text-xs text-muted">{MODULE_DESCRIPTIONS[module]}</p>
                {status && (
                  <Badge variant={statusColor(status)} className="gap-1">
                    {busy && <Loader2 className="size-2.5 animate-spin" />}
                    {status}
                  </Badge>
                )}
                {enablement?.lastError && <p className="text-xs text-error">{enablement.lastError}</p>}
              </div>
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
