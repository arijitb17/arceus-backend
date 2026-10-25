package com.arijit.gatewayservice.security;

import com.arijit.gatewayservice.config.GatewayProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerificationServiceTest {

    static final String SECRET = "gateway-test-secret-which-is-long-enough-for-hs256-signing";

    private final JwtVerificationService service = new JwtVerificationService(
            new GatewayProperties(new GatewayProperties.Jwt(SECRET), List.of(),
                    new GatewayProperties.RateLimit(1, 1), new GatewayProperties.Cors(List.of())));

    static String token(String secret, String type, Object roles, long ttlMs) {
        var b = Jwts.builder().subject("a@b.com").expiration(new Date(System.currentTimeMillis() + ttlMs));
        if (type != null) b.claim("type", type);
        if (roles != null) b.claim("roles", roles);
        return b.signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }

    @Test
    void acceptsValidAccessToken() {
        var v = service.verify(token(SECRET, "access", List.of("ROLE_USER", "ROLE_ADMIN"), 60_000));

        assertThat(v.subject()).isEqualTo("a@b.com");
        assertThat(v.roles()).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void missingRolesBecomeEmptyList() {
        assertThat(service.verify(token(SECRET, "access", null, 60_000)).roles()).isEmpty();
    }

    @Test
    void rejectsRefreshStyleToken() {
        assertThatThrownBy(() -> service.verify(token(SECRET, "refresh", null, 60_000)))
                .isInstanceOf(JwtException.class).hasMessageContaining("not an access token");
    }

    @Test
    void rejectsTokenWithoutTypeClaim() {
        assertThatThrownBy(() -> service.verify(token(SECRET, null, null, 60_000))).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongSignature() {
        assertThatThrownBy(() -> service.verify(token("a-completely-different-secret-value-of-sufficient-length", "access", null, 60_000)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpired() {
        assertThatThrownBy(() -> service.verify(token(SECRET, "access", null, -5_000))).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> service.verify("nope")).isInstanceOf(JwtException.class);
    }
}
