package com.arijit.gatewayservice.security;

import com.arijit.gatewayservice.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtVerificationService {

    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String CLAIM_ROLES = "roles";

    private final GatewayProperties properties;

    /** Subject (user's email) plus roles (e.g. ["ROLE_USER"]), both pulled off a verified access token. */
    public record VerifiedToken(String subject, List<String> roles) {}

    /**
     * Verifies signature and expiry, then returns the token's subject
     * (the user's email, per how auth-service builds its tokens) and roles.
     * <p>
     * Also enforces that this is specifically an ACCESS token: auth-service
     * tags every token it issues with a "type" claim ("access" or
     * "refresh"). Without this check, a refresh token - which is a validly
     * signed JWT for the same user - would pass straight through the
     * Gateway as if it were an access token, since signature and expiry
     * alone can't tell the two apart.
     *
     * @throws JwtException if the token is malformed, expired, signed with
     *                       the wrong key, or isn't an access token -
     *                       callers should treat any JwtException as
     *                       "reject with 401."
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
