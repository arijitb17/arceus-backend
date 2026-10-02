"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { notificationService, templateService, type ListNotificationsParams, type ListTemplatesParams } from "@/services/notification.service";
import type { CreateTemplateRequest, NotificationChannel, NotificationRequest, UpdateTemplateRequest } from "@/types/notification";
import { getErrorMessage } from "@/lib/api/error";

export function useNotifications(params: ListNotificationsParams) {
  return useQuery({
    queryKey: ["notifications", "history", params],
    queryFn: () => notificationService.search(params),
  });
}

export function useNotificationQueue(params: { channel?: NotificationChannel; page?: number } = {}) {
  return useQuery({
    queryKey: ["notifications", "queue", params],
    queryFn: () => notificationService.queue(params),
    refetchInterval: 15_000,
  });
}

export function useQueueSummary() {
  return useQuery({
    queryKey: ["notifications", "queue-summary"],
    queryFn: notificationService.queueSummary,
    refetchInterval: 15_000,
  });
}

export function useSendNotification() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: NotificationRequest) => notificationService.send(body),
    onSuccess: () => {
      toast.success("Notification queued for delivery.");
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useTemplates(params: ListTemplatesParams) {
  return useQuery({
    queryKey: ["templates", params],
    queryFn: () => templateService.list(params),
  });
}

export function useCreateTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTemplateRequest) => templateService.create(body),
    onSuccess: () => {
      toast.success("Template created.");
      queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useUpdateTemplate(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateTemplateRequest) => templateService.update(id, body),
    onSuccess: () => {
      toast.success("Template updated.");
      queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useToggleTemplateStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => templateService.updateStatus(id, active),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useDeleteTemplate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => templateService.delete(id),
    onSuccess: () => {
      toast.success("Template deleted.");
      queryClient.invalidateQueries({ queryKey: ["templates"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}
