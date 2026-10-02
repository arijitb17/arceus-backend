import type { Role } from "@/types/auth";

/**
 * Single source of truth for "who can see what" in the merged portal.
 *
 * The admin-portal and developer-portal frontends used to be two separate
 * Next.js apps (two separate deployments, two separate ports). They've been
 * merged into this one app; this module is what keeps them from bleeding
 * into each other now that they share a session.
 *
 * Route prefixes not listed in ADMIN_ONLY_PREFIXES are treated as available
 * to any authenticated user, regardless of role (e.g. personal settings,
 * personal API keys, the developer workspace under /organizations).
 */
export const ADMIN_ONLY_PREFIXES = [
  "/dashboard",
  "/auth/users",
  "/auth/roles",
  "/auth/permissions",
  "/auth/sessions",
  "/notifications",
  "/rate-limiter",
  "/cache",
  "/scheduler",
  "/feature-flags",
  "/monitoring",
] as const;

export function hasRole(roles: Role[] | undefined, role: Role): boolean {
  return !!roles?.includes(role);
}

export function isAdmin(roles: Role[] | undefined): boolean {
  return hasRole(roles, "ROLE_ADMIN");
}

/** The landing page a user should end up on right after login / at "/". */
export function defaultHomeFor(roles: Role[] | undefined): string {
  return isAdmin(roles) ? "/dashboard" : "/organizations";
}

/** Is this pathname gated to admins only? */
export function isAdminOnlyPath(pathname: string): boolean {
  return ADMIN_ONLY_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/** Can a user with these roles view this pathname? */
export function canAccessPath(pathname: string, roles: Role[] | undefined): boolean {
  if (isAdminOnlyPath(pathname)) return isAdmin(roles);
  return true;
}
