"use client";

import { useState } from "react";
import Link from "next/link";
import { Users as UsersIcon } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { SearchInput } from "@/components/shared/search-input";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/shared/empty-error-state";
import { Pagination } from "@/components/shared/pagination";
import { useUsers } from "@/hooks/use-users";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { getErrorMessage } from "@/lib/api/error";

function initials(name: string) {
  return name.split(" ").map((p) => p[0]).slice(0, 2).join("").toUpperCase();
}

export default function UsersPage() {
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const debouncedQuery = useDebouncedValue(query);

  const { data, isLoading, isError, error, refetch } = useUsers({ query: debouncedQuery || undefined, page, size: 20 });

  return (
    <AppShell title="Users">
      <div className="space-y-6">
        <PageHeader title="Users" description="Every account on the Arceus platform" />

        <Card>
          <div className="flex items-center gap-3 border-b border-border p-3">
            <SearchInput
              value={query}
              onChange={(v) => {
                setQuery(v);
                setPage(0);
              }}
              placeholder="Search by name or email..."
              className="max-w-xs"
            />
          </div>

          {isLoading && (
            <CardContent className="space-y-2">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            </CardContent>
          )}

          {isError && (
            <CardContent>
              <ErrorState message={getErrorMessage(error)} onRetry={() => refetch()} />
            </CardContent>
          )}

          {data && data.content.length === 0 && (
            <CardContent>
              <EmptyState icon={UsersIcon} title="No users found" description="Try a different search." />
            </CardContent>
          )}

          {data && data.content.length > 0 && (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>User</TableHead>
                    <TableHead>Roles</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Verified</TableHead>
                    <TableHead>Provider</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((user) => (
                    <TableRow key={user.id} className="cursor-pointer">
                      <TableCell>
                        <Link href={`/auth/users/${user.id}`} className="flex items-center gap-2.5">
                          <Avatar>
                            <AvatarFallback>{initials(user.name)}</AvatarFallback>
                          </Avatar>
                          <div className="min-w-0">
                            <p className="font-medium truncate">{user.name}</p>
                            <p className="text-xs text-muted truncate">{user.email}</p>
                          </div>
                        </Link>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-1">
                          {user.roles.map((r) => (
                            <Badge key={r} variant={r === "ROLE_ADMIN" ? "accent" : "default"}>
                              {r.replace("ROLE_", "")}
                            </Badge>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={user.accountEnabled ? "success" : "error"} dot>
                          {user.accountEnabled ? "Enabled" : "Disabled"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted">
                        {user.emailVerified && user.phoneVerified ? "Fully verified" : "Partial"}
                      </TableCell>
                      <TableCell className="text-muted">{user.provider}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                pageSize={data.size}
                onPageChange={setPage}
              />
            </>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
