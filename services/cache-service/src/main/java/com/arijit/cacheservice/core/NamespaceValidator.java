package com.arijit.cacheservice.core;

import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class NamespaceValidator {
    private static final Pattern VALID_NAMESPACE = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final CacheProperties cacheProperties;

    public void validate(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new ApiException("namespace is required", HttpStatus.BAD_REQUEST);
        }
        if (!VALID_NAMESPACE.matcher(namespace).matches()) {
            throw new ApiException(
                    "namespace '%s' is invalid - use up to 64 alphanumeric/underscore/hyphen characters".formatted(namespace),
                    HttpStatus.BAD_REQUEST);
        }

        CacheProperties.Namespaces namespaces = cacheProperties.namespaces();
        if (namespaces.allowUnlisted()) {
            return;
        }

        Set<String> allowed = Set.copyOf(namespaces.allowed());
        if (!allowed.contains(namespace)) {
            throw new ApiException(
                    "namespace '%s' is not in the allowed list %s".formatted(namespace, allowed),
                    HttpStatus.FORBIDDEN);
        }
    }

    public void validatePatternIsNamespaceScoped(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new ApiException("pattern is required", HttpStatus.BAD_REQUEST);
        }
        int separatorIndex = pattern.indexOf(':');
        String candidateNamespace = separatorIndex > 0 ? pattern.substring(0, separatorIndex) : pattern.replace("*", "");
        if (candidateNamespace.isBlank() || candidateNamespace.contains("*")) {
            throw new ApiException(
                    "pattern must be scoped to a single namespace prefix, e.g. 'session:user:*' - got '%s'".formatted(pattern),
                    HttpStatus.BAD_REQUEST);
        }
        validate(candidateNamespace);
    }
}
