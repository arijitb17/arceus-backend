import { gatewayClient } from "@/lib/api/gateway-client";
import type {
  AddMemberRequest,
  CreateOrganizationRequest,
  MemberResponse,
  OrganizationResponse,
} from "@/types/organization";

// Routed through the Gateway at /api/organizations/** - downstream prefix
// is the same, /organizations (organization-service's own controllers).
const BASE = "/api/organizations";

export const organizationService = {
  list: () => gatewayClient.get<OrganizationResponse[]>(BASE).then((r) => r.data),
  get: (organizationId: string) => gatewayClient.get<OrganizationResponse>(`${BASE}/${organizationId}`).then((r) => r.data),
  create: (body: CreateOrganizationRequest) => gatewayClient.post<OrganizationResponse>(BASE, body).then((r) => r.data),
  delete: (organizationId: string) => gatewayClient.delete<void>(`${BASE}/${organizationId}`),

  listMembers: (organizationId: string) =>
    gatewayClient.get<MemberResponse[]>(`${BASE}/${organizationId}/members`).then((r) => r.data),
  addMember: (organizationId: string, body: AddMemberRequest) =>
    gatewayClient.post<MemberResponse>(`${BASE}/${organizationId}/members`, body).then((r) => r.data),
  removeMember: (organizationId: string, userEmail: string) =>
    gatewayClient.delete<void>(`${BASE}/${organizationId}/members/${encodeURIComponent(userEmail)}`),
};
