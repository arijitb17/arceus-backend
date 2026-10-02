"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { rbacService, type ListPermissionsParams } from "@/services/rbac.service";
import type { Role } from "@/types/auth";
import type {
  CreatePermissionRequest,
  UpdatePermissionRequest,
  UpdateRoleDetailsRequest,
  UpdateRolePermissionsRequest,
} from "@/types/rbac";
import { getErrorMessage } from "@/lib/api/error";

export function usePermissions(params: ListPermissionsParams) {
  return useQuery({
    queryKey: ["permissions", params],
    queryFn: () => rbacService.listPermissions(params),
  });
}

export function useCreatePermission() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePermissionRequest) => rbacService.createPermission(body),
    onSuccess: () => {
      toast.success("Permission created.");
      queryClient.invalidateQueries({ queryKey: ["permissions"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useUpdatePermission(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdatePermissionRequest) => rbacService.updatePermission(id, body),
    onSuccess: () => {
      toast.success("Permission updated.");
      queryClient.invalidateQueries({ queryKey: ["permissions"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useDeletePermission() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => rbacService.deletePermission(id),
    onSuccess: () => {
      toast.success("Permission deleted.");
      queryClient.invalidateQueries({ queryKey: ["permissions"] });
      queryClient.invalidateQueries({ queryKey: ["roles"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useRoles() {
  return useQuery({ queryKey: ["roles"], queryFn: rbacService.listRoles });
}

export function useUpdateRoleDetails(roleKey: Role) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateRoleDetailsRequest) => rbacService.updateRoleDetails(roleKey, body),
    onSuccess: () => {
      toast.success("Role updated.");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}

export function useUpdateRolePermissions(roleKey: Role) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateRolePermissionsRequest) => rbacService.updateRolePermissions(roleKey, body),
    onSuccess: () => {
      toast.success("Role permissions updated.");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
}
