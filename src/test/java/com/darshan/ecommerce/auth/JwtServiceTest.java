package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.common.security.UserPrincipal;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789";
    private static final JwtProperties PROPERTIES =
            new JwtProperties(SECRET, "ecommerce-backend", Duration.ofMinutes(15), Duration.ofDays(7));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-15T10:15:30Z"));
    private final JwtService jwtService = new JwtService(PROPERTIES, clock);
    private final UserPrincipal principal =
            new UserPrincipal(7L, "alice@example.com", "hash", Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

    @Test
    void issuesATokenWhoseSubjectIsTheEmail() {
        String token = jwtService.issueAccessToken(principal);

        assertThat(jwtService.subjectOf(token)).isEqualTo("alice@example.com");
        assertThat(jwtService.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void acceptsTheTokenUntilTheTtlElapses() {
        String token = jwtService.issueAccessToken(principal);

        clock.advance(Duration.ofMinutes(14));
        assertThat(jwtService.subjectOf(token)).isEqualTo("alice@example.com");

        clock.advance(Duration.ofMinutes(2));
        assertThatThrownBy(() -> jwtService.subjectOf(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        JwtService other = new JwtService(new JwtProperties("another-secret-0123456789abcdef0123456789",
                "ecommerce-backend", Duration.ofMinutes(15), Duration.ofDays(7)), clock);

        String token = other.issueAccessToken(principal);

        assertThatThrownBy(() -> jwtService.subjectOf(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        JwtService other = new JwtService(new JwtProperties(SECRET, "someone-else",
                Duration.ofMinutes(15), Duration.ofDays(7)), clock);

        String token = other.issueAccessToken(principal);

        assertThatThrownBy(() -> jwtService.subjectOf(token)).isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> jwtService.subjectOf("not.a.jwt")).isInstanceOf(MalformedJwtException.class);
    }
}
