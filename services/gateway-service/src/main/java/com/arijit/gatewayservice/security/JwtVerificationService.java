package com.arijit.gatewayservice.security;

import com.arijit.gatewayservice.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtVerificationService {
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String CLAIM_ROLES = "roles";

    private final GatewayProperties properties;

    public record VerifiedToken(String subject, List<String> roles) {}

    /**
     * Enforces that this is specifically an access token: without this check, a refresh token - a
     * validly signed JWT for the same user - would pass straight through the Gateway, since
     * signature and expiry alone can't tell the two apart. Callers should treat any JwtException
     * as "reject with 401."
     */
    @SuppressWarnings("unchecked")
    public VerifiedToken verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
            log.debug("JWT rejected: token type '{}' is not an access token", tokenType);
            throw new JwtException("Token is not an access token");
        }

        List<?> rawRoles = claims.get(CLAIM_ROLES, List.class);
        List<String> roles = rawRoles == null ? List.of() : (List<String>) rawRoles;
        return new VerifiedToken(claims.getSubject(), roles);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.jwt().secret().getBytes());
    }
}
