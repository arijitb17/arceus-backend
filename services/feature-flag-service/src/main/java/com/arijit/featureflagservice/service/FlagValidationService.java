package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.dto.CreateFlagRequest;
import com.arijit.featureflagservice.dto.UpdateFlagRequest;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.TargetType;
import com.arijit.featureflagservice.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Everything Bean Validation on the DTOs alone can't express: cross-field
 * rules (targetType vs environment), the "USER/ROLE/TENANT are reserved"
 * gate, and scheduling sanity checks. Kept separate from FlagService so
 * it's trivially unit-testable without a Spring context or a database -
 * same split as scheduler-service's JobValidationService.
 */
@Service
public class FlagValidationService {

    public void validateCreate(CreateFlagRequest request) {
        TargetType targetType = request.targetType() != null ? request.targetType() : TargetType.ENVIRONMENT;
        requireImplementedTargetType(targetType);

        if (targetType == TargetType.ENVIRONMENT && request.environment() == Environment.GLOBAL) {
            throw new ApiException("targetType ENVIRONMENT requires a specific environment (DEVELOPMENT/STAGING/PRODUCTION), not GLOBAL", HttpStatus.BAD_REQUEST);
        }
        if (targetType == TargetType.ENVIRONMENT && request.environment() == null) {
            throw new ApiException("environment is required when targetType is ENVIRONMENT", HttpStatus.BAD_REQUEST);
        }
        if (request.value() == null) {
            throw new ApiException("value is required", HttpStatus.BAD_REQUEST);
        }
        validateSchedule(request.activateAt(), request.deactivateAt());
    }

    public void validateUpdate(UpdateFlagRequest request) {
        validateSchedule(request.activateAt(), request.deactivateAt());
    }

    private void validateSchedule(Instant activateAt, Instant deactivateAt) {
        if (activateAt != null && deactivateAt != null && !deactivateAt.isAfter(activateAt)) {
            throw new ApiException("deactivateAt must be after activateAt", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireImplementedTargetType(TargetType targetType) {
        if (targetType == TargetType.USER || targetType == TargetType.ROLE || targetType == TargetType.TENANT) {
            throw new ApiException(
                    targetType + " targeting is not yet supported - only GLOBAL and ENVIRONMENT targeting are implemented today",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
