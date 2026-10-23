package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// Transactional boundary for refresh tokens. Rotation reports its outcome as a value instead of
// throwing, because a revocation written during a rejected request must still be committed;
// an exception leaving this method would roll it back together with the request.
@Service
@Transactional
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository,
                               JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public sealed interface RotationResult permits Rotated, Rejected {
    }

    public record Rotated(User user, String rawToken) implements RotationResult {
    }

    public record Rejected(Reason reason) implements RotationResult {
        public enum Reason { UNKNOWN, REUSED, EXPIRED }
    }

    public String issue(Long userId) {
        return store(userRepository.getReferenceById(userId), UUID.randomUUID().toString());
    }

    // The presented token is retired and a successor in the same family is handed out. A token
    // that was already retired being presented again means it was copied, so the whole family
    // is revoked and the legitimate client has to log in again.
    public RotationResult rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHash(OpaqueTokens.hash(rawToken)).orElse(null);
        if (current == null) {
            return new Rejected(Rejected.Reason.UNKNOWN);
        }
        Instant now = clock.instant();
        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}, revoked {} token(s) of family {}",
                    current.getUser().getId(), revoked, current.getFamilyId());
            return new Rejected(Rejected.Reason.REUSED);
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            return new Rejected(Rejected.Reason.EXPIRED);
        }

        String successorRaw = OpaqueTokens.generate();
        RefreshToken successor = repository.save(new RefreshToken(current.getUser(),
                OpaqueTokens.hash(successorRaw), current.getFamilyId(), now.plus(properties.refreshTokenTtl())));
        current.replaceWith(successor, now);
        return new Rotated(current.getUser(), successorRaw);
    }

    // Logout is idempotent and silent: an unknown token gets the same response as a valid one.
    public void revoke(String rawToken) {
        repository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private String store(User user, String familyId) {
        String raw = OpaqueTokens.generate();
        repository.save(new RefreshToken(user, OpaqueTokens.hash(raw), familyId,
                clock.instant().plus(properties.refreshTokenTtl())));
        return raw;
    }
}
