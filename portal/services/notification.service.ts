import { gatewayClient } from "@/lib/api/gateway-client";
import type { PagedResponse } from "@/types/api";
import type {
  CreateTemplateRequest,
  NotificationChannel,
  NotificationRequest,
  NotificationResponse,
  NotificationStatus,
  QueueSummaryResponse,
  TemplateResponse,
  UpdateTemplateRequest,
} from "@/types/notification";

// Routed through the Gateway at /api/notifications/** (downstream prefix is
// also /notifications, so paths match notification-service's controllers 1:1).
const BASE = "/api/notifications";

export interface ListNotificationsParams {
  channel?: NotificationChannel;
  status?: NotificationStatus;
  template?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const notificationService = {
  send: (body: NotificationRequest) =>
    gatewayClient.post<NotificationResponse>(BASE, body).then((r) => r.data),
  get: (id: string) => gatewayClient.get<NotificationResponse>(`${BASE}/${id}`).then((r) => r.data),
  search: (params: ListNotificationsParams = {}) =>
    gatewayClient.get<PagedResponse<NotificationResponse>>(BASE, { params }).then((r) => r.data),
  queue: (params: { channel?: NotificationChannel; page?: number; size?: number } = {}) =>
    gatewayClient.get<PagedResponse<NotificationResponse>>(`${BASE}/queue`, { params }).then((r) => r.data),
  queueSummary: () => gatewayClient.get<QueueSummaryResponse>(`${BASE}/queue/summary`).then((r) => r.data),
};

const TEMPLATES_BASE = "/api/notifications/templates";

export interface ListTemplatesParams {
  channel?: NotificationChannel;
  page?: number;
  size?: number;
}

export const templateService = {
  list: (params: ListTemplatesParams = {}) =>
    gatewayClient.get<PagedResponse<TemplateResponse>>(TEMPLATES_BASE, { params }).then((r) => r.data),
  get: (id: string) => gatewayClient.get<TemplateResponse>(`${TEMPLATES_BASE}/${id}`).then((r) => r.data),
  create: (body: CreateTemplateRequest) =>
    gatewayClient.post<TemplateResponse>(TEMPLATES_BASE, body).then((r) => r.data),
  update: (id: string, body: UpdateTemplateRequest) =>
    gatewayClient.put<TemplateResponse>(`${TEMPLATES_BASE}/${id}`, body).then((r) => r.data),
  updateStatus: (id: string, active: boolean) =>
    gatewayClient.patch<TemplateResponse>(`${TEMPLATES_BASE}/${id}/status`, { active }).then((r) => r.data),
  delete: (id: string) => gatewayClient.delete<void>(`${TEMPLATES_BASE}/${id}`),
};
