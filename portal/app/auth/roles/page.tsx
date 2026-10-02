"use client";

import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Fingerprint } from "lucide-react";
import { RolePermissionsDialog } from "@/features/rbac/role-permissions-dialog";
import { useRoles } from "@/hooks/use-rbac";

export default function RolesPage() {
  const { data: roles, isLoading } = useRoles();

  return (
    <AppShell title="Roles">
      <div className="space-y-6">
        <PageHeader
          title="Roles"
          description="ROLE_USER and ROLE_ADMIN are the only roles Spring Security recognizes - what's editable here is each role's display info and permission set"
        />

        {isLoading && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Skeleton className="h-48" />
            <Skeleton className="h-48" />
          </div>
        )}

        {roles && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {roles.map((role) => (
              <Card key={role.id}>
                <CardHeader className="flex-row items-center justify-between space-y-0">
                  <div className="flex items-center gap-2.5">
                    <div className="flex size-8 items-center justify-center rounded-md bg-accent/15 text-accent">
                      <Fingerprint className="size-4" />
                    </div>
                    <div>
                      <CardTitle>{role.name}</CardTitle>
                      <p className="text-[11px] text-muted font-data mt-0.5">{role.roleKey}</p>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {role.description && <p className="text-sm text-muted">{role.description}</p>}
                  <div>
                    <p className="text-[11px] text-muted mb-2">{role.permissions.length} permission{role.permissions.length === 1 ? "" : "s"}</p>
                    <div className="flex flex-wrap gap-1.5">
                      {role.permissions.length === 0 && <span className="text-xs text-muted-foreground">None assigned</span>}
                      {role.permissions.slice(0, 8).map((p) => (
                        <Badge key={p.id} variant="outline" className="font-data">
                          {p.code}
                        </Badge>
                      ))}
                      {role.permissions.length > 8 && <Badge variant="default">+{role.permissions.length - 8} more</Badge>}
                    </div>
                  </div>
                  <RolePermissionsDialog role={role} />
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
