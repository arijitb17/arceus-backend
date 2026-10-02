export interface AuditLogResponse {
  id: string;
  eventType: string;
  actorEmail: string | null;
  description: string;
  metadata: string | null;
  occurredAt: string;
  receivedAt: string;
}
