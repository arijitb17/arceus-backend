"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Laptop, ShieldCheck, ShieldX } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { ErrorState, EmptyState } from "@/components/shared/empty-error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import {
  useUser,
  useUserSessions,
  useUpdateUserRoles,
  useUpdateUserStatus,
  useRevokeSession,
  useRevokeAllSessions,
} from "@/hooks/use-users";
import { formatDateTime } from "@/lib/utils";
import { getErrorMessage } from "@/lib/api/error";
import type { Role } from "@/types/auth";

const ALL_ROLES: Role[] = ["ROLE_USER", "ROLE_ADMIN"];

function initials(name: string) {
  return name.split(" ").map((p) => p[0]).slice(0, 2).join("").toUpperCase();
}

export default function UserDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data: user, isLoading, isError, error, refetch } = useUser(id);
  const { data: sessions } = useUserSessions(id);

  const updateRoles = useUpdateUserRoles(id);
  const updateStatus = useUpdateUserStatus(id);
  const revokeSession = useRevokeSession(id);
  const revokeAllSessions = useRevokeAllSessions(id);

  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [revokeAllOpen, setRevokeAllOpen] = useState(false);

  useEffect(() => {
    if (user) setSelectedRoles(user.roles);
  }, [user]);

  function toggleRole(role: Role, checked: boolean) {
    setSelectedRoles((prev) => (checked ? [...prev, role] : prev.filter((r) => r !== role)));
  }

  const rolesChanged = user && JSON.stringify([...selectedRoles].sort()) !== JSON.stringify([...user.roles].sort());

  return (
    <AppShell title="User Detail">
      <div className="space-y-6 max-w-4xl">
        <Link href="/auth/users" className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-foreground">
          <ArrowLeft className="size-3.5" />
          Back to Users
        </Link>

        {isLoading && <Skeleton className="h-40" />}
        {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

        {user && (
          <>
            <Card>
              <CardContent className="flex items-center justify-between gap-4 pt-5">
                <div className="flex items-center gap-3">
                  <Avatar className="size-12">
                    <AvatarFallback className="text-base">{initials(user.name)}</AvatarFallback>
                  </Avatar>
                  <div>
                    <h2 className="text-lg font-semibold text-foreground">{user.name}</h2>
                    <p className="text-sm text-muted">{user.email}</p>
                    <p className="text-xs text-muted-foreground mt-0.5">{user.phoneNumber}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-xs text-muted">{user.accountEnabled ? "Enabled" : "Disabled"}</span>
                  <Switch
                    checked={user.accountEnabled}
                    onCheckedChange={(checked) => updateStatus.mutate({ enabled: checked })}
                    disabled={updateStatus.isPending}
                  />
                </div>
              </CardContent>
            </Card>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Card>
                <CardContent className="pt-4">
                  <p className="text-[11px] text-muted mb-1">Provider</p>
                  <p className="text-sm font-medium">{user.provider}</p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-4">
                  <p className="text-[11px] text-muted mb-1">Email verified</p>
                  <div className="flex items-center gap-1.5 text-sm font-medium">
                    {user.emailVerified ? <ShieldCheck className="size-3.5 text-success" /> : <ShieldX className="size-3.5 text-error" />}
                    {user.emailVerified ? "Yes" : "No"}
                  </div>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="pt-4">
                  <p className="text-[11px] text-muted mb-1">Phone verified</p>
                  <div className="flex items-center gap-1.5 text-sm font-medium">
                    {user.phoneVerified ? <ShieldCheck className="size-3.5 text-success" /> : <ShieldX className="size-3.5 text-error" />}
                    {user.phoneVerified ? "Yes" : "No"}
                  </div>
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardHeader>
                <CardTitle>Roles</CardTitle>
                <CardDescription>Replaces this user&apos;s full role set when saved</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {ALL_ROLES.map((role) => (
                  <label key={role} className="flex items-center gap-2.5 text-sm cursor-pointer">
                    <Checkbox
                      checked={selectedRoles.includes(role)}
                      onCheckedChange={(checked) => toggleRole(role, checked === true)}
                    />
                    {role.replace("ROLE_", "")}
                  </label>
                ))}
                {rolesChanged && (
                  <Button
                    size="sm"
                    loading={updateRoles.isPending}
                    onClick={() => updateRoles.mutate({ roles: selectedRoles })}
                  >
                    Save role changes
                  </Button>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex-row items-center justify-between space-y-0">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <Laptop className="size-4 text-muted" />
                    Active Sessions
                  </CardTitle>
                  <CardDescription>One entry per logged-in device</CardDescription>
                </div>
                {sessions && sessions.content.length > 0 && (
                  <Button variant="outline" size="sm" onClick={() => setRevokeAllOpen(true)}>
                    Revoke all
                  </Button>
                )}
              </CardHeader>
              <CardContent>
                {!sessions && <Skeleton className="h-16" />}
                {sessions && sessions.content.length === 0 && (
                  <EmptyState icon={Laptop} title="No active sessions" />
                )}
                {sessions && sessions.content.length > 0 && (
                  <ul className="space-y-2">
                    {sessions.content.map((s) => (
                      <li
                        key={s.id}
                        className="flex items-center justify-between rounded-md border border-border bg-surface px-3 py-2.5"
                      >
                        <div>
                          <p className="text-sm text-foreground font-data">{s.id.slice(0, 8)}</p>
                          <p className="text-[11px] text-muted-foreground">
                            Issued {formatDateTime(s.createdAt)} &middot; Expires {formatDateTime(s.expiresAt)}
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <Badge variant={s.expired ? "default" : "success"}>{s.expired ? "Expired" : "Active"}</Badge>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => revokeSession.mutate(s.id)}
                            disabled={revokeSession.isPending}
                          >
                            Revoke
                          </Button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </>
        )}
      </div>

      <ConfirmDialog
        open={revokeAllOpen}
        onOpenChange={setRevokeAllOpen}
        title="Revoke all sessions?"
        description="Every device this user is logged in on will be signed out immediately."
        confirmLabel="Revoke all"
        loading={revokeAllSessions.isPending}
        onConfirm={() => revokeAllSessions.mutate(undefined, { onSuccess: () => setRevokeAllOpen(false) })}
      />
    </AppShell>
  );
}
