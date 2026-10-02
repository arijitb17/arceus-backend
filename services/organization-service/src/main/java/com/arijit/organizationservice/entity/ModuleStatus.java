package com.arijit.organizationservice.entity;

/**
 * Lifecycle of a single (project, environment, module) enablement - see
 * ModuleEnablement and ModuleProvisioningClient's javadoc for how a module
 * moves through this now that provisioning is a direct synchronous call
 * rather than an event-driven pipeline.
 */
public enum ModuleStatus {
    /** Legacy/defensive value - enable() no longer sets this itself, but a
     *  row could still be seen here if it was created before this change. */
    PENDING,
    /** Set briefly while enable() calls the owning service's ProvisionTenant RPC, within the same request. */
    PROVISIONING,
    /** Provisioned successfully - the module is usable via the project's API keys. */
    ACTIVE,
    /** Provisioning failed - see lastError. Developer can retry, which re-attempts the same RPC call. */
    FAILED,
    /** Set briefly while disable() calls the owning service's DeprovisionTenant RPC, within the same request. */
    DISABLING,
    /** Explicitly disabled - resources archived per the 30-day retention policy, not hard-deleted. */
    DISABLED
}
