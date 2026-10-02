"use client";

import * as React from "react";
import { useState } from "react";
import { ScrollText, ChevronDown, ChevronRight } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { FilterBar } from "@/components/shared/filter-bar";
import { SearchInput } from "@/components/shared/search-input";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { useAuditLogs } from "@/hooks/use-audit";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { getErrorMessage } from "@/lib/api/error";
import { formatDateTime } from "@/lib/utils";

export default function AuditPage() {
  const [q, setQ] = useState("");
  const [eventType, setEventType] = useState("");
  const [actorEmail, setActorEmail] = useState("");
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);

  const debouncedQ = useDebouncedValue(q);
  const debouncedEventType = useDebouncedValue(eventType);
  const debouncedActor = useDebouncedValue(actorEmail);

  const { data, isLoading, isError, error, refetch } = useAuditLogs({
    q: debouncedQ || undefined,
    eventType: debouncedEventType || undefined,
    actorEmail: debouncedActor || undefined,
    page,
    size: 25,
  });

  return (
    <AppShell title="Audit">
      <div className="space-y-6">
        <PageHeader title="Audit Log" description="Every domain event recorded across the platform" />

        <Card>
          <div className="flex flex-wrap items-center gap-3 border-b border-border p-3">
            <SearchInput
              value={q}
              onChange={(v) => {
                setQ(v);
                setPage(0);
              }}
              placeholder="Search description..."
              className="max-w-xs"
            />
            <FilterBar>
              <Input
                value={eventType}
                onChange={(e) => {
                  setEventType(e.target.value);
                  setPage(0);
                }}
                placeholder="Event type..."
                className="h-9 w-40 font-data"
              />
              <Input
                value={actorEmail}
                onChange={(e) => {
                  setActorEmail(e.target.value);
                  setPage(0);
                }}
                placeholder="Actor email..."
                className="h-9 w-48"
              />
            </FilterBar>
          </div>

          {isLoading && (
            <CardContent className="space-y-2">
              {Array.from({ length: 8 }).map((_, i) => (
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
              <EmptyState icon={ScrollText} title="No audit events found" description="Try widening your filters." />
            </CardContent>
          )}

          {data && data.items.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead />
                    <TableHead>Event Type</TableHead>
                    <TableHead>Actor</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead>Occurred</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((log) => (
                    <React.Fragment key={log.id}>
                      <TableRow
                        className={log.metadata ? "cursor-pointer" : undefined}
                        onClick={() => log.metadata && setExpanded(expanded === log.id ? null : log.id)}
                      >
                        <TableCell className="w-6 text-muted">
                          {log.metadata ? expanded === log.id ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" /> : null}
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline" className="font-data">
                            {log.eventType}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-muted">{log.actorEmail || "system"}</TableCell>
                        <TableCell className="max-w-md truncate">{log.description}</TableCell>
                        <TableCell className="text-muted">{formatDateTime(log.occurredAt)}</TableCell>
                      </TableRow>
                      {expanded === log.id && log.metadata && (
                        <TableRow>
                          <TableCell colSpan={5} className="bg-surface">
                            <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-all font-data text-xs text-muted">
                              {log.metadata}
                            </pre>
                          </TableCell>
                        </TableRow>
                      )}
                    </React.Fragment>
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
