export type OrgRole = "OWNER" | "ADMIN" | "MEMBER";

/** GET/POST /api/organizations - organization-service's OrganizationController. */
export interface OrganizationResponse {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  ownerUserId: string;
  callerRole: OrgRole;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrganizationRequest {
  name: string;
  slug: string;
  description?: string;
}

export interface MemberResponse {
  id: string;
  userId: string; // the member's email - see OrganizationMember's javadoc
  role: OrgRole;
  joinedAt: string;
}

export interface AddMemberRequest {
  userId: string; // email
  role: OrgRole;
}
