"use client";

import { useState } from "react";
import { Clock, ListChecks, Users, ListTree, AlertOctagon } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { FilterBar } from "@/components/shared/filter-bar";
import { SearchInput } from "@/components/shared/search-input";
import { StatCard } from "@/components/shared/stat-card";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { StatusBadge } from "@/components/shared/status-badge";
import {
  useJobs,
  useJobStats,
  usePauseJob,
  useResumeJob,
  useCancelJob,
  useRetryJob,
} from "@/hooks/use-scheduler-jobs";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";
import type { JobStatus } from "@/types/scheduler";

const STATUSES: JobStatus[] = [
  "PENDING",
  "SCHEDULED",
  "RUNNING",
  "COMPLETED",
  "FAILED",
  "RETRYING",
  "CANCELLED",
  "EXPIRED",
  "PAUSED",
];

export default function SchedulerPage() {
  const [serviceName, setServiceName] = useState("");
  const [status, setStatus] = useState<JobStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const debouncedService = useDebouncedValue(serviceName);

  const { data: stats, isLoading: statsLoading } = useJobStats();
  const { data, isLoading, isError, error, refetch } = useJobs({
    serviceName: debouncedService || undefined,
    status: status === "ALL" ? undefined : status,
    page,
    size: 20,
  });

  const pauseJob = usePauseJob();
  const resumeJob = useResumeJob();
  const cancelJob = useCancelJob();
  const retryJob = useRetryJob();

  return (
    <AppShell title="Scheduler">
      <div className="space-y-6">
        <PageHeader title="Scheduler" description="Platform-wide job queue - cron, delayed, and fixed-rate work" />

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard label="Total Jobs" value={stats ? String(stats.totalJobs) : "\u2014"} icon={ListChecks} tone="primary" loading={statsLoading} />
          <StatCard
            label="Worker Pool"
            value={stats ? `${stats.workerActiveCount}/${stats.workerPoolSize}` : "\u2014"}
            icon={Users}
            tone="accent"
            loading={statsLoading}
          />
          <StatCard label="Worker Queue" value={stats ? String(stats.workerQueueSize) : "\u2014"} icon={ListTree} tone="default" loading={statsLoading} />
          <StatCard
            label="Dead Letter"
            value={stats ? String(stats.deadLetterCount) : "\u2014"}
            icon={AlertOctagon}
            tone={stats && stats.deadLetterCount > 0 ? "error" : "default"}
            loading={statsLoading}
          />
        </div>

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
                value={status}
                onValueChange={(v) => {
                  setStatus(v as JobStatus | "ALL");
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
              <EmptyState icon={Clock} title="No jobs found" description="Try widening your filters." />
            </CardContent>
          )}

          {data && data.items.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Job</TableHead>
                    <TableHead>Service</TableHead>
                    <TableHead>Trigger</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Retries</TableHead>
                    <TableHead>Next fire</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((job) => (
                    <TableRow key={job.id}>
                      <TableCell className="font-data">{job.jobName}</TableCell>
                      <TableCell className="text-muted">{job.serviceName}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{job.triggerType}</Badge>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={job.status} />
                      </TableCell>
                      <TableCell className="text-muted tabular-nums">
                        {job.retryCount}/{job.maxRetries}
                      </TableCell>
                      <TableCell className="text-muted">{formatDateTime(job.nextFireAt)}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          {job.status === "PAUSED" ? (
                            <Button variant="ghost" size="sm" onClick={() => resumeJob.mutate(job.id)}>
                              Resume
                            </Button>
                          ) : (
                            ["PENDING", "SCHEDULED", "RUNNING"].includes(job.status) && (
                              <Button variant="ghost" size="sm" onClick={() => pauseJob.mutate(job.id)}>
                                Pause
                              </Button>
                            )
                          )}
                          {job.status === "FAILED" && (
                            <Button variant="ghost" size="sm" onClick={() => retryJob.mutate(job.id)}>
                              Retry
                            </Button>
                          )}
                          {!["COMPLETED", "CANCELLED", "EXPIRED"].includes(job.status) && (
                            <Button variant="ghost" size="sm" onClick={() => cancelJob.mutate(job.id)}>
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
