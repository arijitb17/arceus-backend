package com.arijit.cacheservice.core;

import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamespaceValidatorTest {

    private CacheProperties propertiesWith(boolean allowUnlisted) {
        return new CacheProperties(
                new CacheProperties.Pool(16, 8, 0, 2000),
                3600L,
                com.arijit.cacheservice.dto.SerializationFormat.JSON,
                5_242_880L,
                new CacheProperties.Compression(true, 8192L),
                new CacheProperties.Namespaces(List.of("session", "auth"), allowUnlisted)
        );
    }

    @Test
    void allowsListedNamespace() {
        NamespaceValidator validator = new NamespaceValidator(propertiesWith(false));
        assertDoesNotThrow(() -> validator.validate("session"));
    }

    @Test
    void rejectsUnlistedNamespaceByDefault() {
        NamespaceValidator validator = new NamespaceValidator(propertiesWith(false));
        assertThrows(ApiException.class, () -> validator.validate("not-configured"));
    }

    @Test
    void allowsUnlistedWhenConfigured() {
        NamespaceValidator validator = new NamespaceValidator(propertiesWith(true));
        assertDoesNotThrow(() -> validator.validate("anything"));
    }

    @Test
    void rejectsBlankNamespace() {
        NamespaceValidator validator = new NamespaceValidator(propertiesWith(true));
        assertThrows(ApiException.class, () -> validator.validate(""));
    }

    @Test
    void patternMustBeNamespaceScoped() {
        NamespaceValidator validator = new NamespaceValidator(propertiesWith(false));
        assertThrows(ApiException.class, () -> validator.validatePatternIsNamespaceScoped("*"));
        assertDoesNotThrow(() -> validator.validatePatternIsNamespaceScoped("session:user:*"));
    }
}
