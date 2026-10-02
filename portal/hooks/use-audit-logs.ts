"use client";

import { useQuery } from "@tanstack/react-query";
import { auditLogService } from "@/services/audit-log.service";

export function useProjectAuditLogs(organizationId: string, projectId: string, page = 0) {
  return useQuery({
    queryKey: ["organizations", organizationId, "projects", projectId, "logs", page],
    queryFn: () => auditLogService.list(organizationId, projectId, page),
    enabled: !!organizationId && !!projectId,
    // Logs are append-only and developers often leave this tab open while
    // testing - a light poll keeps it feeling live without needing SSE.
    refetchInterval: 15_000,
  });
}
