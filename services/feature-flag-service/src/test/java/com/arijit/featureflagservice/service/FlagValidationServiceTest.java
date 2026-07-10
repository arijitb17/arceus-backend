package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.dto.CreateFlagRequest;
import com.arijit.featureflagservice.dto.UpdateFlagRequest;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.entity.TargetType;
import com.arijit.featureflagservice.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class FlagValidationServiceTest {

    private final FlagValidationService validationService = new FlagValidationService();

    private CreateFlagRequest baseRequest(Environment environment, TargetType targetType) {
        return new CreateFlagRequest("test.flag", "Test Flag", "desc", FlagType.BOOLEAN,
                environment, targetType, true, 100, null, null, "tester");
    }

    @Test
    void validEnvironmentTargetedFlagPasses() {
        assertDoesNotThrow(() -> validationService.validateCreate(baseRequest(Environment.PRODUCTION, TargetType.ENVIRONMENT)));
    }

    @Test
    void defaultsToEnvironmentTargetTypeWhenOmitted() {
        CreateFlagRequest request = new CreateFlagRequest("k", "n", null, FlagType.BOOLEAN,
                Environment.STAGING, null, true, null, null, null, null);
        assertDoesNotThrow(() -> validationService.validateCreate(request));
    }

    @Test
    void environmentTargetTypeRejectsGlobalEnvironment() {
        assertThrows(ApiException.class, () -> validationService.validateCreate(baseRequest(Environment.GLOBAL, TargetType.ENVIRONMENT)));
    }

    @Test
    void environmentTargetTypeRequiresAnEnvironment() {
        assertThrows(ApiException.class, () -> validationService.validateCreate(baseRequest(null, TargetType.ENVIRONMENT)));
    }

    @Test
    void globalTargetTypePasses() {
        assertDoesNotThrow(() -> validationService.validateCreate(baseRequest(null, TargetType.GLOBAL)));
    }

    @Test
    void userTargetingIsRejectedAsNotYetImplemented() {
        assertThrows(ApiException.class, () -> validationService.validateCreate(baseRequest(Environment.PRODUCTION, TargetType.USER)));
    }

    @Test
    void roleTargetingIsRejectedAsNotYetImplemented() {
        assertThrows(ApiException.class, () -> validationService.validateCreate(baseRequest(Environment.PRODUCTION, TargetType.ROLE)));
    }

    @Test
    void tenantTargetingIsRejectedAsNotYetImplemented() {
        assertThrows(ApiException.class, () -> validationService.validateCreate(baseRequest(Environment.PRODUCTION, TargetType.TENANT)));
    }

    @Test
    void nullValueIsRejected() {
        CreateFlagRequest request = new CreateFlagRequest("k", "n", null, FlagType.BOOLEAN,
                Environment.PRODUCTION, TargetType.ENVIRONMENT, null, null, null, null, null);
        assertThrows(ApiException.class, () -> validationService.validateCreate(request));
    }

    @Test
    void deactivateAtBeforeActivateAtIsRejected() {
        Instant now = Instant.now();
        CreateFlagRequest request = new CreateFlagRequest("k", "n", null, FlagType.BOOLEAN,
                Environment.PRODUCTION, TargetType.ENVIRONMENT, true, null,
                now.plus(1, ChronoUnit.HOURS), now, "tester");
        assertThrows(ApiException.class, () -> validationService.validateCreate(request));
    }

    @Test
    void validScheduleOnUpdatePasses() {
        Instant now = Instant.now();
        UpdateFlagRequest request = new UpdateFlagRequest(null, null, null, null, now, now.plus(1, ChronoUnit.HOURS), null);
        assertDoesNotThrow(() -> validationService.validateUpdate(request));
    }
}
