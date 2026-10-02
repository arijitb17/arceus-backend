"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { FolderKanban, Users, UserPlus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { CreateProjectDialog } from "@/features/projects/create-project-dialog";
import { useOrganization, useOrganizationMembers, useAddMember, useRemoveMember } from "@/hooks/use-organizations";
import { useProjects } from "@/hooks/use-projects";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { OrgRole } from "@/types/organization";

export default function OrganizationDetailPage() {
  const { organizationId } = useParams<{ organizationId: string }>();

  const { data: organization, isLoading: orgLoading } = useOrganization(organizationId);
  const { data: projects, isLoading: projectsLoading, isError, error, refetch } = useProjects(organizationId);
  const { data: members, isLoading: membersLoading } = useOrganizationMembers(organizationId);

  const addMember = useAddMember(organizationId);
  const removeMember = useRemoveMember(organizationId);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<OrgRole>("MEMBER");
  const [removeTarget, setRemoveTarget] = useState<string | null>(null);

  const canManage = organization?.callerRole === "OWNER" || organization?.callerRole === "ADMIN";

  async function onInvite() {
    if (!inviteEmail.trim()) return;
    try {
      await addMember.mutateAsync({ userId: inviteEmail.trim(), role: inviteRole });
      toast.success(`${inviteEmail} added.`);
      setInviteEmail("");
    } catch (error) {
      toast.error(getErrorMessage(error, "Couldn't add that member."));
    }
  }

  async function onRemove(userEmail: string) {
    try {
      await removeMember.mutateAsync(userEmail);
      toast.success("Member removed.");
    } catch (error) {
      toast.error(getErrorMessage(error, "Couldn't remove that member."));
    } finally {
      setRemoveTarget(null);
    }
  }

  return (
    <AppShell title={organization?.name ?? "Organization"}>
      <div className="space-y-6">
        <PageHeader
          title={orgLoading ? "\u2026" : (organization?.name ?? "Organization")}
          description={organization?.slug}
          actions={<CreateProjectDialog organizationId={organizationId} />}
        />

        <Tabs defaultValue="projects">
          <TabsList>
            <TabsTrigger value="projects">
              <FolderKanban className="size-3.5" />
              Projects
            </TabsTrigger>
            <TabsTrigger value="members">
              <Users className="size-3.5" />
              Members
            </TabsTrigger>
          </TabsList>

          <TabsContent value="projects" className="mt-4">
            {projectsLoading && (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-24" />
                ))}
              </div>
            )}

            {isError && <ErrorState message={getErrorMessage(error)} onRetry={refetch} />}

            {projects && projects.length === 0 && (
              <EmptyState
                icon={FolderKanban}
                title="No projects yet"
                description="Create a project to connect a repo and start enabling modules."
              />
            )}

            {projects && projects.length > 0 && (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {projects.map((project) => (
                  <Link key={project.id} href={`/organizations/${organizationId}/projects/${project.id}`}>
                    <Card className="h-full transition-colors hover:border-primary/50">
                      <CardContent className="space-y-2 p-5">
                        <h3 className="font-medium text-foreground">{project.name}</h3>
                        <p className="text-xs text-muted">{project.slug}</p>
                        {project.description && <p className="text-xs text-muted line-clamp-2">{project.description}</p>}
                      </CardContent>
                    </Card>
                  </Link>
                ))}
              </div>
            )}
          </TabsContent>

          <TabsContent value="members" className="mt-4">
            <Card>
              {canManage && (
                <div className="flex items-center gap-2 border-b border-border p-3">
                  <Input
                    placeholder="email@company.com"
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                    className="max-w-xs"
                  />
                  <Select value={inviteRole} onValueChange={(v) => setInviteRole(v as OrgRole)}>
                    <SelectTrigger className="w-32">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ADMIN">Admin</SelectItem>
                      <SelectItem value="MEMBER">Member</SelectItem>
                    </SelectContent>
                  </Select>
                  <Button onClick={onInvite} loading={addMember.isPending}>
                    <UserPlus />
                    Add
                  </Button>
                </div>
              )}

              {membersLoading && (
                <CardContent className="space-y-2">
                  {Array.from({ length: 3 }).map((_, i) => (
                    <Skeleton key={i} className="h-8" />
                  ))}
                </CardContent>
              )}

              {members && (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Email</TableHead>
                      <TableHead>Role</TableHead>
                      <TableHead>Joined</TableHead>
                      {canManage && <TableHead className="w-10" />}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {members.map((member) => (
                      <TableRow key={member.id}>
                        <TableCell>{member.userId}</TableCell>
                        <TableCell>
                          <Badge variant="outline">{member.role}</Badge>
                        </TableCell>
                        <TableCell className="text-muted">{formatDateTime(member.joinedAt)}</TableCell>
                        {canManage && (
                          <TableCell>
                            {member.role !== "OWNER" && (
                              <Button variant="ghost" size="icon" onClick={() => setRemoveTarget(member.userId)}>
                                <Trash2 className="size-3.5" />
                              </Button>
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Card>
          </TabsContent>
        </Tabs>

        <ConfirmDialog
          open={!!removeTarget}
          onOpenChange={(open) => !open && setRemoveTarget(null)}
          title="Remove member?"
          description={`${removeTarget} will lose access to this organization's projects.`}
          confirmLabel="Remove"
          onConfirm={() => removeTarget && onRemove(removeTarget)}
          loading={removeMember.isPending}
        />
      </div>
    </AppShell>
  );
}
