"use client";

import { Gauge } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { ModuleUnavailable } from "@/components/shared/module-unavailable";

export default function RateLimiterPage() {
  return (
    <AppShell title="Rate Limiter">
      <div className="space-y-6">
        <PageHeader title="Rate Limiter" description="Backs the platform's @RateLimited aspect" />
        <ModuleUnavailable
          icon={Gauge}
          title="Not a browser-facing module"
          description="ratelimit-service is internal-only by design - it backs the @RateLimited aspect that other services call directly over gRPC, and doesn't expose a REST API through the Gateway. There's nothing here for this console to manage."
        />
      </div>
    </AppShell>
  );
}
