"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userManagementService, type ListUsersParams } from "@/services/user-management.service";
import type { UpdateAccountStatusRequest, UpdateRolesRequest } from "@/types/user-management";
import { getErrorMessage } from "@/lib/api/error";

export function useUsers(params: ListUsersParams) {
  return useQuery({
    queryKey: ["users", params],
    queryFn: () => userManagementService.list(params),
  });
}

export function useUser(id: string | undefined) {
  return useQuery({
    queryKey: ["users", "detail", id],
    queryFn: () => userManagementService.get(id!),
    enabled: !!id,
  });
}

export function useUserSessions(id: string | undefined, page = 0) {
  return useQuery({
    queryKey: ["users", "sessions", id, page],
    queryFn: () => userManagementService.listSessions(id!, page),
    enabled: !!id,
  });
}

export function useUpdateUserRoles(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateRolesRequest) => userManagementService.updateRoles(id, body),
    onSuccess: () => {
      toast.success("Roles updated.");
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useUpdateUserStatus(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateAccountStatusRequest) => userManagementService.updateStatus(id, body),
    onSuccess: (data) => {
      toast.success(data.accountEnabled ? "Account enabled." : "Account disabled.");
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useRevokeSession(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => userManagementService.revokeSession(userId, sessionId),
    onSuccess: () => {
      toast.success("Session revoked.");
      queryClient.invalidateQueries({ queryKey: ["users", "sessions", userId] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useRevokeAllSessions(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => userManagementService.revokeAllSessions(userId),
    onSuccess: () => {
      toast.success("All sessions revoked.");
      queryClient.invalidateQueries({ queryKey: ["users", "sessions", userId] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}
