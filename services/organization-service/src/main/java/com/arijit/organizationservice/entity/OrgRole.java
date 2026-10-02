package com.arijit.organizationservice.entity;

/**
 * A user's role within one specific {@link Organization} - distinct from
 * auth-service's platform-level ROLE_USER/ROLE_ADMIN, which govern access
 * to the Admin Portal. This is the Developer Portal's own authorization
 * axis: someone can be ORG_OWNER of one organization and have no access at
 * all to another, regardless of their platform role.
 */
public enum OrgRole {
    /** Created the organization, or was promoted. Can delete the org, manage billing, add/remove any member. */
    OWNER,
    /** Can manage projects, modules, secrets, and members - cannot delete the org or remove the OWNER. */
    ADMIN,
    /** Can view and work within projects they're added to - cannot manage membership or secrets by default. */
    MEMBER
}
