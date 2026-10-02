"use client";

import { useState } from "react";
import { Rocket } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { FilterBar } from "@/components/shared/filter-bar";
import { SearchInput } from "@/components/shared/search-input";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { StatusBadge } from "@/components/shared/status-badge";
import { CreateDeploymentDialog } from "@/features/deployment/create-deployment-dialog";
import { RollbackDeploymentDialog } from "@/features/deployment/rollback-deployment-dialog";
import {
  useDeployments,
  useApproveDeployment,
  useDeployDeployment,
  useCancelDeployment,
} from "@/hooks/use-deployments";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { DeploymentEnvironment, DeploymentStatus } from "@/types/deployment";

const ENVIRONMENTS: DeploymentEnvironment[] = ["DEVELOPMENT", "TESTING", "STAGING", "PRODUCTION"];
const STATUSES: DeploymentStatus[] = [
  "CREATED",
  "PENDING",
  "APPROVED",
  "DEPLOYING",
  "RUNNING",
  "FAILED",
  "ROLLED_BACK",
  "CANCELLED",
];

export default function DeploymentPage() {
  const [serviceName, setServiceName] = useState("");
  const [environment, setEnvironment] = useState<DeploymentEnvironment | "ALL">("ALL");
  const [status, setStatus] = useState<DeploymentStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const debouncedService = useDebouncedValue(serviceName);

  const { data, isLoading, isError, error, refetch } = useDeployments({
    serviceName: debouncedService || undefined,
    environment: environment === "ALL" ? undefined : environment,
    status: status === "ALL" ? undefined : status,
    page,
    size: 20,
  });

  const approve = useApproveDeployment();
  const deploy = useDeployDeployment();
  const cancel = useCancelDeployment();

  return (
    <AppShell title="Deployment">
      <div className="space-y-6">
        <PageHeader title="Deployments" description="Service rollouts across every environment" actions={<CreateDeploymentDialog />} />

        <Card>
          <div className="flex flex-wrap items-center gap-3 border-b border-border p-3">
            <SearchInput
              value={serviceName}
              onChange={(v) => {
                setServiceName(v);
                setPage(0);
              }}
              placeholder="Filter by service..."
              className="max-w-xs"
            />
            <FilterBar>
              <Select
                value={environment}
                onValueChange={(v) => {
                  setEnvironment(v as DeploymentEnvironment | "ALL");
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-40">
                  <SelectValue placeholder="Environment" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All environments</SelectItem>
                  {ENVIRONMENTS.map((e) => (
                    <SelectItem key={e} value={e}>
                      {e}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={status}
                onValueChange={(v) => {
                  setStatus(v as DeploymentStatus | "ALL");
                  setPage(0);
                }}
              >
                <SelectTrigger className="w-36">
                  <SelectValue placeholder="Status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All statuses</SelectItem>
                  {STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {s}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FilterBar>
          </div>

          {isLoading && (
            <CardContent className="space-y-2">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-10" />
              ))}
            </CardContent>
          )}

          {isError && (
            <CardContent>
              <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />
            </CardContent>
          )}

          {data && data.items.length === 0 && (
            <CardContent>
              <EmptyState icon={Rocket} title="No deployments found" description="Try widening your filters, or create the first one." />
            </CardContent>
          )}

          {data && data.items.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Service</TableHead>
                    <TableHead>Version</TableHead>
                    <TableHead>Environment</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Created by</TableHead>
                    <TableHead>Updated</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((d) => (
                    <TableRow key={d.id}>
                      <TableCell className="font-data">{d.serviceName}</TableCell>
                      <TableCell className="text-muted font-data">{d.serviceVersion}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{d.environment}</Badge>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={d.status} />
                      </TableCell>
                      <TableCell className="text-muted">{d.createdBy}</TableCell>
                      <TableCell className="text-muted">{formatDateTime(d.updatedAt)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          {d.status === "PENDING" && d.requiresApproval && (
                            <Button variant="ghost" size="sm" onClick={() => approve.mutate(d.id)}>
                              Approve
                            </Button>
                          )}
                          {(d.status === "APPROVED" || d.status === "CREATED") && (
                            <Button variant="ghost" size="sm" onClick={() => deploy.mutate(d.id)}>
                              Deploy
                            </Button>
                          )}
                          {d.status === "RUNNING" && <RollbackDeploymentDialog deployment={d} />}
                          {!["RUNNING", "FAILED", "ROLLED_BACK", "CANCELLED"].includes(d.status) && (
                            <Button variant="ghost" size="sm" onClick={() => cancel.mutate(d.id)}>
                              Cancel
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} pageSize={data.size} onPageChange={setPage} />
            </>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
