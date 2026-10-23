package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.common.persistence.PersistenceTest;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PersistenceTest
class RefreshTokenPersistenceTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MutableClock clock;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(new User("tokens@example.com", "{noop}irrelevant", "Token Owner", Set.of()));
    }

    @Test
    void rejectsDuplicateTokenHash() {
        refreshTokenRepository.saveAndFlush(token("same-hash", "family-a"));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(token("same-hash", "family-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revokesEveryActiveTokenOfAFamilyOnly() {
        RefreshToken first = refreshTokenRepository.save(token("hash-1", "family-a"));
        RefreshToken second = refreshTokenRepository.save(token("hash-2", "family-a"));
        RefreshToken other = refreshTokenRepository.save(token("hash-3", "family-b"));
        first.replaceWith(second, clock.instant());
        entityManager.flush();
        clock.advance(Duration.ofMinutes(1));

        int revoked = refreshTokenRepository.revokeFamily("family-a", clock.instant());

        assertThat(revoked).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("hash-1")).get()
                .satisfies(t -> assertThat(t.getRevokedAt()).isEqualTo(clock.instant().minus(Duration.ofMinutes(1))));
        assertThat(refreshTokenRepository.findByTokenHash("hash-2")).get()
                .satisfies(t -> assertThat(t.getRevokedAt()).isEqualTo(clock.instant()));
        assertThat(refreshTokenRepository.findByTokenHash("hash-3")).get()
                .satisfies(t -> assertThat(t.isRevoked()).isFalse());
        assertThat(other.getId()).isNotNull();
    }

    @Test
    void loadsTheOwnerAndRolesWithTheToken() {
        refreshTokenRepository.saveAndFlush(token("hash-owner", "family-a"));
        entityManager.clear();

        RefreshToken loaded = refreshTokenRepository.findByTokenHash("hash-owner").orElseThrow();
        entityManager.detach(loaded);

        assertThat(loaded.getUser().getEmail()).isEqualTo("tokens@example.com");
        assertThat(loaded.getUser().getRoles()).isEmpty();
        assertThat(loaded.isExpired(clock.instant())).isFalse();
        assertThat(loaded.isExpired(clock.instant().plus(Duration.ofDays(7)))).isTrue();
    }

    private RefreshToken token(String hash, String family) {
        return new RefreshToken(user, hash, family, clock.instant().plus(Duration.ofDays(7)));
    }
}
