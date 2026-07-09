package com.arijit.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Everything related to actually creating and reading JWTs lives here, in
// one place, so no other class needs to know the details of how a token is
// signed or what library we're using for it.
@Service
public class JwtService {

    // Injected from application.yml (jwt.secret). This is the key used to
    // both sign new tokens and verify incoming ones - if this doesn't match,
    // NOTHING with the old key will validate anymore, so treat it carefully.
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    /** Claim name distinguishing an access token from anything else. */
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** Claim name carrying the user's roles (e.g. ["ROLE_USER"]) on an access token. */
    public static final String CLAIM_ROLES = "roles";

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(UserDetails userDetails, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);
        // Roles ride along on the token itself (rather than requiring a DB
        // lookup) so both the Gateway and any downstream service can
        // enforce RBAC from the token alone - see RoleHeaderAuthFilter in
        // audit-service/notification-service for how the Gateway forwards
        // this after verifying the token.
        claims.put(CLAIM_ROLES, userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        return buildToken(userDetails.getUsername(), claims, accessTokenExpirationMs);
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    private String buildToken(String subject, Map<String, Object> claims, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)       // "who is this token about" - we use the user's email
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        List<?> raw = extractClaim(token, claims -> claims.get(CLAIM_ROLES, List.class));
        return raw == null ? List.of() : (List<String>) raw;
    }

    /**
     * True only for a token that (a) is validly signed and unexpired, (b)
     * belongs to the given user, AND (c) was issued as an ACCESS token.
     * <p>
     * That last check matters: without it, a refresh token - which is a
     * validly-signed JWT for the same user - would authenticate against
     * every protected endpoint just like an access token, defeating the
     * entire point of having two token types with different lifetimes and
     * revocation semantics.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = parseClaims(token);
        String username = claims.getSubject();
        boolean isAccessToken = TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        return username.equals(userDetails.getUsername())
                && isAccessToken
                && claims.getExpiration().after(new Date());
    }

    // parseSignedClaims() re-checks the signature using our secret key - if
    // the token was tampered with, or signed with a different key, this
    // throws instead of silently returning bad data.
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }
}
