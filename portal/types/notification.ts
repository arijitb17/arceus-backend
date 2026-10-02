export type NotificationChannel = "EMAIL" | "SMS" | "PUSH" | "SLACK" | "DISCORD" | "WEBHOOK";
export type NotificationStatus = "PENDING" | "SENT" | "FAILED" | "RETRYING" | "DEAD" | "SUPPRESSED";

export interface NotificationResponse {
  id: string;
  channel: NotificationChannel;
  template: string;
  to: string;
  status: NotificationStatus;
  attempts: number;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  sentAt: string | null;
}

export interface NotificationRequest {
  channel: NotificationChannel;
  template: string;
  to: string;
  variables?: Record<string, unknown>;
}

export interface QueueSummaryResponse {
  pending: number;
  retrying: number;
  dead: number;
  sent: number;
  failed: number;
  suppressed: number;
}

export interface TemplateResponse {
  id: string;
  channel: NotificationChannel;
  code: string;
  subject: string | null;
  body: string;
  active: boolean;
}

export interface CreateTemplateRequest {
  channel: NotificationChannel;
  code: string;
  subject?: string;
  body: string;
}

export interface UpdateTemplateRequest {
  subject?: string;
  body: string;
}
