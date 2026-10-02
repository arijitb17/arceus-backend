"use client";

import { useState } from "react";
import { GitCommitHorizontal, Play, Rocket } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  useApproveDeployment,
  useTenantDeploymentDetail,
  useTenantDeployments,
  useTriggerDeployment,
} from "@/hooks/use-tenant-deployments";
import { useProjectGithubConnection } from "@/hooks/use-projects";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { TenantDeploymentStatus } from "@/types/tenant-deployment";
import type { EnvironmentType } from "@/types/project";

const STATUS_VARIANT: Record<TenantDeploymentStatus, "success" | "error" | "warning" | "default" | "primary"> = {
  BUILD_QUEUED: "default",
  BUILDING: "warning",
  BUILD_FAILED: "error",
  CREATED: "default",
  PENDING: "warning",
  APPROVED: "primary",
  DEPLOYING: "warning",
  RUNNING: "success",
  FAILED: "error",
  ROLLED_BACK: "default",
  CANCELLED: "default",
};

export function DeploymentsPanel({
  organizationId,
  projectId,
  environment,
}: {
  organizationId: string;
  projectId: string;
  environment: EnvironmentType;
}) {
  const { data: connection } = useProjectGithubConnection(organizationId, projectId);
  const { data, isLoading, isError, error, refetch } = useTenantDeployments(organizationId, projectId);
  const triggerDeployment = useTriggerDeployment(organizationId, projectId);
  const approveDeployment = useApproveDeployment(organizationId, projectId);

  const [confirmDeployOpen, setConfirmDeployOpen] = useState(false);
  const [logDeploymentId, setLogDeploymentId] = useState<string | null>(null);
  const { data: detail } = useTenantDeploymentDetail(organizationId, projectId, logDeploymentId);

  async function onDeploy() {
    try {
      await triggerDeployment.mutateAsync(environment);
      toast.success("Build queued.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't trigger a deployment."));
    } finally {
      setConfirmDeployOpen(false);
    }
  }

  async function onApprove(id: string) {
    try {
      await approveDeployment.mutateAsync(id);
      toast.success("Approved - deploying now.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't approve that deployment."));
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-muted">
          {connection
            ? <>Deploys from <span className="font-data text-foreground">{connection.repoFullName}</span> pushes to the tracked branch automatically, or trigger one manually below.</>
            : "Connect a GitHub repository in the GitHub tab to enable push-to-deploy, or trigger a manual build once connected."}
        </p>
        <Button size="sm" disabled={!connection} onClick={() => setConfirmDeployOpen(true)}>
          <Play />
          Deploy now
        </Button>
      </div>

      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      )}

      {isError && <ErrorState message={getErrorMessage(error)} onRetry={refetch} />}

      {data && data.items.length === 0 && (
        <EmptyState
          icon={Rocket}
          title="No deployments yet"
          description="Push to your connected repo's tracked branch, or click Deploy now, to build and deploy this project."
        />
      )}

      {data && data.items.length > 0 && (
        <div className="space-y-2">
          {data.items.map((d) => (
            <Card key={d.id}>
              <CardContent className="flex items-center gap-3 p-4">
                <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary/15 text-primary">
                  <GitCommitHorizontal className="size-4" />
                </div>
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex items-center gap-2">
                    <Badge variant={STATUS_VARIANT[d.status]}>{d.status}</Badge>
                    <span className="text-xs text-muted">{d.environment}</span>
                    {d.sourceCommitSha && (
                      <span className="font-data text-xs text-muted">{d.sourceCommitSha.slice(0, 8)}</span>
                    )}
                    {d.sourceBranch && <span className="text-xs text-muted">on {d.sourceBranch}</span>}
                  </div>
                  <p className="text-xs text-muted">
                    {formatDateTime(d.createdAt)} &middot; {d.createdBy}
                    {d.lastError && <span className="text-red-400"> &middot; {d.lastError}</span>}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  {d.status === "PENDING" && (
                    <Button size="sm" variant="outline" onClick={() => onApprove(d.id)} loading={approveDeployment.isPending}>
                      Approve
                    </Button>
                  )}
                  <Button size="sm" variant="ghost" onClick={() => setLogDeploymentId(d.id)}>
                    Logs
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={confirmDeployOpen}
        onOpenChange={setConfirmDeployOpen}
        title={`Deploy ${environment}?`}
        description="Builds and deploys the current HEAD of your connected repo's tracked branch."
        confirmLabel="Deploy"
        onConfirm={onDeploy}
        loading={triggerDeployment.isPending}
      />

      <Dialog open={!!logDeploymentId} onOpenChange={(open) => !open && setLogDeploymentId(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Build log</DialogTitle>
            <DialogDescription>
              {detail?.summary.status} &middot; {detail?.summary.sourceCommitSha?.slice(0, 8)}
            </DialogDescription>
          </DialogHeader>
          <DialogBody>
            <pre className="max-h-96 overflow-auto rounded-md bg-background p-4 text-xs font-mono text-muted whitespace-pre-wrap">
              {detail?.buildLog || "No build log for this deployment."}
            </pre>
          </DialogBody>
        </DialogContent>
      </Dialog>
    </div>
  );
}
