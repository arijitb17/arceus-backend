import { gatewayClient } from "@/lib/api/gateway-client";
import type { PagedResponse } from "@/types/api";
import type { AuditLogResponse } from "@/types/audit";

// Routed through the Gateway at /api/audit/** - downstream prefix is
// /audit-logs (audit-service's own controller).
const BASE = "/api/audit";

export interface ListAuditLogsParams {
  eventType?: string;
  actorEmail?: string;
  from?: string;
  to?: string;
  q?: string;
  page?: number;
  size?: number;
}

export const auditService = {
  search: (params: ListAuditLogsParams = {}) =>
    gatewayClient.get<PagedResponse<AuditLogResponse>>(BASE, { params }).then((r) => r.data),
  get: (id: string) => gatewayClient.get<AuditLogResponse>(`${BASE}/${id}`).then((r) => r.data),
};
