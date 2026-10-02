"use client";

import { useState } from "react";
import { History } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/shared/empty-error-state";
import { useFlagHistory } from "@/hooks/use-feature-flags";
import { formatDateTime } from "@/lib/utils";

export function FlagHistoryDialog({ flagKey }: { flagKey: string }) {
  const [open, setOpen] = useState(false);
  const { data, isLoading } = useFlagHistory(open ? flagKey : undefined);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <History />
          History
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>History for {flagKey}</DialogTitle>
          <DialogDescription>Every recorded change to this flag, most recent first.</DialogDescription>
        </DialogHeader>
        <DialogBody className="space-y-3">
          {isLoading && (
            <div className="space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            </div>
          )}
          {!isLoading && (!data || data.items.length === 0) && <EmptyState icon={History} title="No history recorded yet" />}
          {data &&
            data.items.map((h) => (
              <div key={h.id} className="rounded-md border border-border bg-surface p-3 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <Badge variant="outline" className="font-data">
                    {h.action}
                  </Badge>
                  <span className="text-xs text-muted">{formatDateTime(h.timestamp)}</span>
                </div>
                <p className="mt-1.5 text-xs text-muted">by {h.actor}</p>
                {(h.oldValue || h.newValue) && (
                  <p className="mt-1.5 font-data text-xs text-muted-foreground">
                    {h.oldValue ?? "\u2014"} {"\u2192"} {h.newValue ?? "\u2014"}
                  </p>
                )}
              </div>
            ))}
        </DialogBody>
      </DialogContent>
    </Dialog>
  );
}
