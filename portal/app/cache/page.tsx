"use client";

import { useState } from "react";
import { Database, HardDrive, Percent, Layers, TimerReset, Search, Trash2 } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { StatCard } from "@/components/shared/stat-card";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/shared/empty-error-state";
import { ConfirmDialog } from "@/components/shared/confirm-dialog";
import { CacheEntryDialog } from "@/features/cache/cache-entry-dialog";
import { useCacheStats, useCacheLookup, useDeleteCacheEntry, useClearNamespace } from "@/hooks/use-cache";
import { getErrorMessage } from "@/lib/api/error";

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function CachePage() {
  const { data: stats, isLoading: statsLoading } = useCacheStats();

  const [namespace, setNamespace] = useState("");
  const [key, setKey] = useState("");
  const [lookupTarget, setLookupTarget] = useState<{ namespace: string; key: string } | null>(null);
  const { data: entry, isLoading: entryLoading, isError, error } = useCacheLookup(
    lookupTarget?.namespace ?? "",
    lookupTarget?.key ?? "",
    !!lookupTarget
  );

  const deleteEntry = useDeleteCacheEntry();
  const clearNamespace = useClearNamespace();
  const [clearTarget, setClearTarget] = useState<string | null>(null);

  return (
    <AppShell title="Cache">
      <div className="space-y-6">
        <PageHeader title="Cache" description="Redis-backed key/value store - entries are looked up by namespace + key" actions={<CacheEntryDialog />} />

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
          <StatCard label="Total Keys" value={stats ? String(stats.totalKeys) : "\u2014"} icon={Layers} tone="primary" loading={statsLoading} />
          <StatCard
            label="Hit Ratio"
            value={stats ? `${(stats.hitRatio * 100).toFixed(1)}%` : "\u2014"}
            icon={Percent}
            tone={stats && stats.hitRatio > 0.8 ? "success" : "warning"}
            loading={statsLoading}
          />
          <StatCard label="Memory Used" value={stats ? formatBytes(stats.memoryUsageBytes) : "\u2014"} icon={HardDrive} tone="accent" loading={statsLoading} />
          <StatCard label="Expired Keys" value={stats ? String(stats.expiredKeys) : "\u2014"} icon={TimerReset} tone="default" loading={statsLoading} />
          <StatCard label="Evictions" value={stats ? String(stats.evictionCount) : "\u2014"} icon={Trash2} tone="warning" loading={statsLoading} />
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Look up an entry</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="namespace">Namespace</Label>
                <Input id="namespace" className="w-48 font-data" value={namespace} onChange={(e) => setNamespace(e.target.value)} placeholder="e.g. sessions" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="key">Key</Label>
                <Input id="key" className="w-64 font-data" value={key} onChange={(e) => setKey(e.target.value)} placeholder="e.g. user:1234" />
              </div>
              <Button
                disabled={!namespace || !key}
                onClick={() => setLookupTarget({ namespace, key })}
              >
                <Search />
                Look up
              </Button>
              {namespace && (
                <Button variant="outline" onClick={() => setClearTarget(namespace)}>
                  Clear namespace
                </Button>
              )}
            </div>

            {!lookupTarget && (
              <EmptyState icon={Database} title="No entry looked up yet" description="Enter a namespace and key above, since there's no way to list all cache keys." />
            )}

            {lookupTarget && entryLoading && <p className="text-sm text-muted">Looking up {lookupTarget.namespace}/{lookupTarget.key}\u2026</p>}

            {lookupTarget && isError && (
              <div className="rounded-md border border-border bg-surface p-4 text-sm text-muted">
                {getErrorMessage(error, "Entry not found.")}
              </div>
            )}

            {lookupTarget && entry && (
              <div className="space-y-3 rounded-md border border-border bg-surface p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-data text-sm text-foreground">
                      {entry.namespace}/{entry.key}
                    </span>
                    <Badge variant="outline">{entry.type}</Badge>
                    <Badge variant="outline">{entry.ttlPolicy}</Badge>
                  </div>
                  <div className="flex gap-2">
                    <CacheEntryDialog existing={entry} />
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        deleteEntry.mutate(
                          { namespace: entry.namespace, key: entry.key },
                          { onSuccess: () => setLookupTarget(null) }
                        )
                      }
                    >
                      Delete
                    </Button>
                  </div>
                </div>
                <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-all font-data text-xs text-muted">
                  {JSON.stringify(entry.value, null, 2)}
                </pre>
                <p className="text-xs text-muted-foreground">
                  TTL remaining: {entry.ttlSecondsRemaining >= 0 ? `${entry.ttlSecondsRemaining}s` : "no expiry"}
                </p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <ConfirmDialog
        open={!!clearTarget}
        onOpenChange={(open) => !open && setClearTarget(null)}
        title={`Clear namespace "${clearTarget}"?`}
        description="Every key under this namespace will be deleted. This can't be undone."
        loading={clearNamespace.isPending}
        onConfirm={() => {
          if (clearTarget) clearNamespace.mutate(clearTarget, { onSuccess: () => setClearTarget(null) });
        }}
      />
    </AppShell>
  );
}
