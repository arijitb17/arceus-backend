package com.arijit.organizationservice.entity;

/**
 * The Arceus modules a project can enable per environment. Each maps to
 * exactly one existing service that owns the actual provisioning logic
 * behind its ProvisionTenant/DeprovisionTenant gRPC method -
 * ModuleProvisioningClient dispatches on this enum to call the right one,
 * so it deliberately mirrors service names rather than introducing
 * separate "product" naming.
 */
public enum ModuleType {
    AUTHENTICATION,
    NOTIFICATION,
    CACHE,
    SCHEDULER,
    FEATURE_FLAGS
}
