"use client";

import { useQuery } from "@tanstack/react-query";
import { auditService, type ListAuditLogsParams } from "@/services/audit.service";

export function useAuditLogs(params: ListAuditLogsParams) {
  return useQuery({
    queryKey: ["audit", params],
    queryFn: () => auditService.search(params),
  });
}
