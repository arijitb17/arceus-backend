"use client";

import { useState } from "react";
import Link from "next/link";
import { Laptop, ArrowRight } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { SearchInput } from "@/components/shared/search-input";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/shared/empty-error-state";
import { useUsers } from "@/hooks/use-users";
import { useDebouncedValue } from "@/hooks/use-debounced-value";

function initials(name: string) {
  return name.split(" ").map((p) => p[0]).slice(0, 2).join("").toUpperCase();
}

export default function SessionsPage() {
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebouncedValue(query);
  const { data, isLoading } = useUsers({ query: debouncedQuery || undefined, size: 10 });

  return (
    <AppShell title="Sessions">
      <div className="space-y-6 max-w-2xl">
        <PageHeader
          title="Sessions"
          description="A session is one logged-in device. Find a user to view and revoke theirs."
        />

        <SearchInput value={query} onChange={setQuery} placeholder="Search users by name or email..." />

        <Card>
          {isLoading && (
            <CardContent className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            </CardContent>
          )}

          {!isLoading && (!data || data.content.length === 0) && (
            <CardContent>
              <EmptyState icon={Laptop} title={query ? "No users found" : "Search for a user"} description="Sessions are managed from each user's detail page." />
            </CardContent>
          )}

          {data && data.content.length > 0 && (
            <ul className="divide-y divide-border">
              {data.content.map((user) => (
                <li key={user.id}>
                  <Link
                    href={`/auth/users/${user.id}`}
                    className="flex items-center justify-between gap-3 px-4 py-3 hover:bg-surface/60 transition-colors"
                  >
                    <div className="flex items-center gap-2.5">
                      <Avatar>
                        <AvatarFallback>{initials(user.name)}</AvatarFallback>
                      </Avatar>
                      <div>
                        <p className="text-sm font-medium">{user.name}</p>
                        <p className="text-xs text-muted">{user.email}</p>
                      </div>
                    </div>
                    <ArrowRight className="size-3.5 text-muted-foreground" />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </AppShell>
  );
}
