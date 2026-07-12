"use client";

import { Clock } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Badge } from "@/components/ui/badge";
import { useRunningJobs, useSchedulerStats } from "@/hooks/use-dashboard";
import { getErrorMessage } from "@/lib/api/error";

export function RunningJobsCard() {
  const { data, isLoading, isError, error, refetch } = useRunningJobs(5);
  const { data: stats } = useSchedulerStats();
  const jobs = data?.items ?? [];

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <div>
          <CardTitle className="flex items-center gap-2">
            <Clock className="size-4 text-muted" />
            Running Jobs
          </CardTitle>
          <CardDescription>Scheduler executions in progress</CardDescription>
        </div>
        {stats && (
          <Badge variant="primary">
            {stats.workerActiveCount}/{stats.workerPoolSize} workers
          </Badge>
        )}
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-10" />
            ))}
          </div>
        )}

        {isError && <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />}

        {!isLoading && !isError && jobs.length === 0 && (
          <EmptyState icon={Clock} title="No jobs running" description="Scheduled jobs will appear here while executing." />
        )}

        {!isLoading && jobs.length > 0 && (
          <ul className="space-y-2">
            {jobs.map((job) => (
              <li
                key={job.id}
                className="flex items-center justify-between gap-3 rounded-md border border-border bg-surface px-3 py-2"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">{job.jobName}</p>
                  <p className="text-[11px] text-muted-foreground truncate">{job.serviceName}</p>
                </div>
                <span className="size-1.5 shrink-0 rounded-full bg-primary animate-pulse-slow" />
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
