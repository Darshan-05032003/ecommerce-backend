package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.common.security.UserPrincipal;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

// Access tokens are HS256 JWTs signed with the configured secret. The parser is built once
// (it is thread-safe) and reads "now" from the application Clock so expiry is testable.
@Component
public class JwtService {

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String issueAccessToken(UserPrincipal principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(principal.getEmail())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public String subjectOf(String token) throws JwtException {
        return parser.parseSignedClaims(token).getPayload().getSubject();
    }

    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }
}
