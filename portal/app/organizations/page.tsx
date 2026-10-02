"use client";

import Link from "next/link";
import { Building2 } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { CreateOrganizationDialog } from "@/features/organizations/create-organization-dialog";
import { useOrganizations } from "@/hooks/use-organizations";
import { getErrorMessage } from "@/lib/api/error";

export default function OrganizationsPage() {
  const { data: organizations, isLoading, isError, error, refetch } = useOrganizations();

  return (
    <AppShell title="Organizations">
      <div className="space-y-6">
        <PageHeader
          title="Organizations"
          description="Everything you build on Arceus lives under an organization."
          actions={<CreateOrganizationDialog />}
        />

        {isLoading && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-28" />
            ))}
          </div>
        )}

        {isError && <ErrorState message={getErrorMessage(error)} onRetry={refetch} />}

        {organizations && organizations.length === 0 && (
          <EmptyState
            icon={Building2}
            title="No organizations yet"
            description="Create your first organization to start building on Arceus."
          />
        )}

        {organizations && organizations.length > 0 && (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {organizations.map((org) => (
              <Link key={org.id} href={`/organizations/${org.id}`}>
                <Card className="h-full transition-colors hover:border-primary/50">
                  <CardContent className="space-y-3 p-5">
                    <div className="flex items-center justify-between">
                      <div className="flex size-9 items-center justify-center rounded-md bg-primary/15 text-primary">
                        <Building2 className="size-4" />
                      </div>
                      <Badge variant="outline">{org.callerRole}</Badge>
                    </div>
                    <div>
                      <h3 className="font-medium text-foreground">{org.name}</h3>
                      <p className="text-xs text-muted mt-0.5">{org.slug}</p>
                    </div>
                    {org.description && <p className="text-xs text-muted line-clamp-2">{org.description}</p>}
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
