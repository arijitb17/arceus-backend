package com.arijit.authservice.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-definitely-long-enough-for-hs256";

    private JwtService jwt;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "secret", SECRET);
        ReflectionTestUtils.setField(jwt, "accessTokenExpirationMs", 900_000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpirationMs", 604_800_000L);
        user = User.builder().username("ash@pallet.town").password("x")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                .build();
    }

    @Test
    void roundTripsSubjectAndRoles() {
        String token = jwt.generateAccessToken(user, Map.of("name", "Ash"));

        assertThat(jwt.extractUsername(token)).isEqualTo("ash@pallet.town");
        assertThat(jwt.extractRoles(token)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void validForMatchingUser() {
        assertThat(jwt.isTokenValid(jwt.generateAccessToken(user, Map.of()), user)).isTrue();
    }

    @Test
    void invalidForDifferentUser() {
        UserDetails other = User.builder().username("misty@cerulean.city").password("x").authorities("ROLE_USER").build();

        assertThat(jwt.isTokenValid(jwt.generateAccessToken(user, Map.of()), other)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String forged = Jwts.builder().subject("ash@pallet.town")
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret-123".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwt.isTokenValid(forged, user)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        ReflectionTestUtils.setField(jwt, "accessTokenExpirationMs", -1_000L);
        String expired = jwt.generateAccessToken(user, Map.of());

        assertThatThrownBy(() -> jwt.extractUsername(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void nonAccessTypeTokenIsNotValid() {
        String refreshLike = Jwts.builder().subject("ash@pallet.town").claim("type", "refresh")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwt.isTokenValid(refreshLike, user)).isFalse();
    }

    @Test
    void tokenWithoutRolesYieldsEmptyList() {
        String noRoles = Jwts.builder().subject("a").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        assertThat(jwt.extractRoles(noRoles)).isEmpty();
    }

    @Test
    void malformedTokenRejected() {
        assertThatThrownBy(() -> jwt.extractUsername("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void extraClaimsCannotOverrideTypeOrRoles() {
        String token = jwt.generateAccessToken(user, Map.of("type", "refresh", "roles", List.of("ROLE_SUPER")));

        assertThat(jwt.isTokenValid(token, user)).isTrue();
        assertThat(jwt.extractRoles(token)).doesNotContain("ROLE_SUPER");
    }

    @Test
    void exposesExpirations() {
        assertThat(jwt.getAccessTokenExpirationSeconds()).isEqualTo(900);
        assertThat(jwt.getRefreshTokenExpirationMs()).isEqualTo(604_800_000L);
    }
}
