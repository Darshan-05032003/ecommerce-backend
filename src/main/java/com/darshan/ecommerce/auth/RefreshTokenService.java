package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.common.security.InvalidTokenException;
import com.darshan.ecommerce.user.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String INVALID = "Invalid or expired refresh token";

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    public record Rotation(User user, String rawToken) {
    }

    public String issue(User user) {
        return store(user, UUID.randomUUID().toString());
    }

    // Rotation: the presented token is retired and a successor in the same family is handed out.
    // A token that was already retired being presented again means it was copied, so the whole
    // family is revoked and the legitimate client has to log in again.
    public Rotation rotate(String rawToken) {
        RefreshToken current = find(rawToken);
        Instant now = clock.instant();

        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}, revoked {} token(s) of family {}",
                    current.getUser().getId(), revoked, current.getFamilyId());
            throw new InvalidTokenException(INVALID);
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw new InvalidTokenException(INVALID);
        }

        String successorRaw = OpaqueTokens.generate();
        RefreshToken successor = repository.save(
                new RefreshToken(current.getUser(), OpaqueTokens.hash(successorRaw), current.getFamilyId(),
                        now.plus(properties.refreshTokenTtl())));
        current.replaceWith(successor, now);
        return new Rotation(current.getUser(), successorRaw);
    }

    // Logout is idempotent and silent: an unknown token gets the same response as a valid one.
    public void revoke(String rawToken) {
        repository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private RefreshToken find(String rawToken) {
        return repository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException(INVALID));
    }

    private String store(User user, String familyId) {
        String raw = OpaqueTokens.generate();
        repository.save(new RefreshToken(user, OpaqueTokens.hash(raw), familyId,
                clock.instant().plus(properties.refreshTokenTtl())));
        return raw;
    }
}
