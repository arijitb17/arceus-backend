"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Github, Unlink } from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { useProjectGithubConnection } from "@/hooks/use-projects";
import { useConnectRepo, useDisconnectRepo, useGithubConnectUrl, useGithubRepositories } from "@/hooks/use-github";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";

/**
 * The install/callback round-trip (architecture design §6) leaves the
 * browser via a full-page redirect to github.com and back - the
 * `installationId` query param on return is how this panel knows a repo
 * picker should appear, since there's no in-memory state left after that
 * navigation.
 */
export function GithubPanel({ organizationId, projectId }: { organizationId: string; projectId: string }) {
  const searchParams = useSearchParams();
  const installationIdFromCallback = searchParams.get("installationId");

  const { data: connection, isLoading, refetch } = useProjectGithubConnection(organizationId, projectId);
  const connectUrl = useGithubConnectUrl(organizationId, projectId);
  const { data: repositories, isLoading: reposLoading } = useGithubRepositories(installationIdFromCallback);
  const connectRepo = useConnectRepo(organizationId, projectId);
  const disconnectRepo = useDisconnectRepo(organizationId, projectId);

  const [selectedRepo, setSelectedRepo] = useState<string>("");
  const [disconnectOpen, setDisconnectOpen] = useState(false);

  useEffect(() => {
    if (installationIdFromCallback) {
      toast.success("GitHub App installed - pick a repository to connect below.");
    }
  }, [installationIdFromCallback]);

  async function onStartConnect() {
    try {
      const { installUrl } = await connectUrl.mutateAsync();
      window.location.href = installUrl;
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't start the GitHub connection."));
    }
  }

  async function onConnectRepo() {
    if (!selectedRepo || !installationIdFromCallback) return;
    const repo = repositories?.find((r) => r.fullName === selectedRepo);
    if (!repo) return;
    try {
      await connectRepo.mutateAsync({
        organizationId,
        installationId: installationIdFromCallback,
        repoFullName: repo.fullName,
        defaultBranch: repo.defaultBranch,
      });
      toast.success(`Connected ${repo.fullName}.`);
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't connect that repository."));
    }
  }

  async function onDisconnect() {
    try {
      await disconnectRepo.mutateAsync();
      toast.success("Repository disconnected.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Couldn't disconnect."));
    } finally {
      setDisconnectOpen(false);
    }
  }

  if (isLoading) return <Skeleton className="h-32" />;

  if (connection) {
    return (
      <Card>
        <CardContent className="flex items-center justify-between gap-4 p-5">
          <div className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-md bg-primary/15 text-primary">
              <Github className="size-4" />
            </div>
            <div>
              <p className="text-sm font-medium text-foreground">{connection.repoFullName}</p>
              <p className="text-xs text-muted">
                Tracking <Badge variant="outline">{connection.defaultBranch}</Badge> &middot; connected{" "}
                {formatDateTime(connection.connectedAt)}
              </p>
            </div>
          </div>
          <Button variant="outline" onClick={() => setDisconnectOpen(true)}>
            <Unlink />
            Disconnect
          </Button>
        </CardContent>
        <ConfirmDialog
          open={disconnectOpen}
          onOpenChange={setDisconnectOpen}
          title="Disconnect repository?"
          description="Pushes to this repo will no longer trigger anything for this project."
          confirmLabel="Disconnect"
          onConfirm={onDisconnect}
          loading={disconnectRepo.isPending}
        />
      </Card>
    );
  }

  if (installationIdFromCallback) {
    return (
      <Card>
        <CardContent className="space-y-4 p-5">
          <div>
            <h3 className="text-sm font-medium text-foreground">Choose a repository</h3>
            <p className="text-xs text-muted mt-0.5">Pick the repo this project should deploy from.</p>
          </div>

          {reposLoading && <Skeleton className="h-9" />}

          {repositories && (
            <div className="flex items-center gap-2">
              <Select value={selectedRepo} onValueChange={setSelectedRepo}>
                <SelectTrigger className="flex-1">
                  <SelectValue placeholder="Select a repository" />
                </SelectTrigger>
                <SelectContent>
                  {repositories.map((repo) => (
                    <SelectItem key={repo.fullName} value={repo.fullName}>
                      {repo.fullName} {repo.isPrivate && "(private)"}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button onClick={onConnectRepo} disabled={!selectedRepo} loading={connectRepo.isPending}>
                Connect
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardContent className="flex flex-col items-center justify-center gap-3 p-10 text-center">
        <div className="flex size-10 items-center justify-center rounded-md bg-surface text-muted">
          <Github className="size-5" />
        </div>
        <div>
          <p className="text-sm font-medium text-foreground">No repository connected</p>
          <p className="text-xs text-muted mt-1 max-w-sm">
            Connect a GitHub repo to enable push-to-deploy for this project.
          </p>
        </div>
        <Button onClick={onStartConnect} loading={connectUrl.isPending}>
          <Github />
          Connect GitHub
        </Button>
      </CardContent>
    </Card>
  );
}
