"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { organizationService } from "@/services/organization.service";
import type { AddMemberRequest, CreateOrganizationRequest } from "@/types/organization";

export function useOrganizations() {
  return useQuery({
    queryKey: ["organizations"],
    queryFn: organizationService.list,
  });
}

export function useOrganization(organizationId: string) {
  return useQuery({
    queryKey: ["organizations", organizationId],
    queryFn: () => organizationService.get(organizationId),
    enabled: !!organizationId,
  });
}

export function useCreateOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateOrganizationRequest) => organizationService.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations"] }),
  });
}

export function useOrganizationMembers(organizationId: string) {
  return useQuery({
    queryKey: ["organizations", organizationId, "members"],
    queryFn: () => organizationService.listMembers(organizationId),
    enabled: !!organizationId,
  });
}

export function useAddMember(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: AddMemberRequest) => organizationService.addMember(organizationId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "members"] }),
  });
}

export function useRemoveMember(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userEmail: string) => organizationService.removeMember(organizationId, userEmail),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "members"] }),
  });
}
