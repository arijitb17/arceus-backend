package com.arijit.deploymentservice.entity;

/**
 * The four environments this platform tracks deployments for. Today the
 * platform runs one instance of each service regardless of environment
 * (there's no per-environment fleet/namespace yet - that's Kubernetes/
 * GitOps territory, explicitly deferred), so {@code environment} on a
 * {@link Deployment} is a tracking + policy attribute (which approval
 * rules apply, which release history a deployment belongs to) rather than
 * a selector for a literal separate running instance. DeploymentProperties'
 * {@code approval.required-environments} is keyed off this enum.
 */
public enum Environment {
    DEVELOPMENT,
    TESTING,
    STAGING,
    PRODUCTION
}
