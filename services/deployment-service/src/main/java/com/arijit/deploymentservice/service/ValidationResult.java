package com.arijit.deploymentservice.service;

import java.util.List;

/**
 * Outcome of one validation/health-verification pass - a list of
 * human-readable findings plus whether any of them were hard failures.
 * {@code passed=true} with a non-empty {@code warnings} list is the
 * "degraded but not blocking" case (e.g. a dependency monitoring-service
 * itself can't currently reach) that DeploymentProperties.Validation#strict()
 * decides whether to escalate to a hard failure or not.
 */
public record ValidationResult(boolean passed, List<String> warnings, List<String> errors) {

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public String summary() {
        if (errors.isEmpty() && warnings.isEmpty()) {
            return "All checks passed";
        }
        StringBuilder sb = new StringBuilder();
        if (!errors.isEmpty()) {
            sb.append("errors: ").append(String.join("; ", errors));
        }
        if (!warnings.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("warnings: ").append(String.join("; ", warnings));
        }
        return sb.toString();
    }
}
