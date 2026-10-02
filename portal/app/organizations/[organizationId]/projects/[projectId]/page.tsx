"use client";

import { useParams } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { ChevronRight, Fingerprint, KeyRound, Lock, Activity } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { useOrganization } from "@/hooks/use-organizations";
import { useProject } from "@/hooks/use-projects";
import { ModulesPanel } from "@/features/modules/modules-panel";
import { ApiKeysPanel } from "@/features/api-keys/api-keys-panel";
import { SecretsPanel } from "@/features/secrets/secrets-panel";
import { UsagePanel } from "@/features/usage/usage-panel";
import { ENVIRONMENTS, type EnvironmentType } from "@/types/project";

export default function ProjectDetailPage() {
  const { organizationId, projectId } = useParams<{ organizationId: string; projectId: string }>();
  const [environment, setEnvironment] = useState<EnvironmentType>("DEVELOPMENT");

  const { data: organization } = useOrganization(organizationId);
  const { data: project, isLoading } = useProject(organizationId, projectId);

  return (
    <AppShell title={project?.name ?? "Project"}>
      <div className="space-y-6">
        <div className="flex items-center gap-1.5 text-xs text-muted">
          <Link href="/organizations" className="hover:text-foreground">
            Organizations
          </Link>
          <ChevronRight className="size-3" />
          <Link href={`/organizations/${organizationId}`} className="hover:text-foreground">
            {organization?.name ?? "\u2026"}
          </Link>
          <ChevronRight className="size-3" />
          <span className="text-foreground">{project?.name ?? "\u2026"}</span>
        </div>

        {isLoading ? (
          <Skeleton className="h-16" />
        ) : (
          <PageHeader
            title={project?.name ?? "Project"}
            description={project?.description ?? project?.slug}
            actions={
              <Select value={environment} onValueChange={(v) => setEnvironment(v as EnvironmentType)}>
                <SelectTrigger className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ENVIRONMENTS.map((env) => (
                    <SelectItem key={env} value={env}>
                      {env}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            }
          />
        )}

        <Tabs defaultValue="modules">
          <TabsList>
            <TabsTrigger value="modules">
              <Fingerprint className="size-3.5" />
              Modules
            </TabsTrigger>
            <TabsTrigger value="api-keys">
              <KeyRound className="size-3.5" />
              API Keys
            </TabsTrigger>
            <TabsTrigger value="secrets">
              <Lock className="size-3.5" />
              Secrets
            </TabsTrigger>
            <TabsTrigger value="usage">
              <Activity className="size-3.5" />
              Usage
            </TabsTrigger>
          </TabsList>

          <TabsContent value="modules" className="mt-4">
            <ModulesPanel organizationId={organizationId} projectId={projectId} environment={environment} />
          </TabsContent>
          <TabsContent value="api-keys" className="mt-4">
            <ApiKeysPanel organizationId={organizationId} projectId={projectId} environment={environment} />
          </TabsContent>
          <TabsContent value="secrets" className="mt-4">
            <SecretsPanel organizationId={organizationId} projectId={projectId} environment={environment} />
          </TabsContent>
          <TabsContent value="usage" className="mt-4">
            <UsagePanel organizationId={organizationId} projectId={projectId} environment={environment} />
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  );
}
