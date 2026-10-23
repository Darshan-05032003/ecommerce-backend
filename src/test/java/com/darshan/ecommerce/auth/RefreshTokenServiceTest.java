package com.darshan.ecommerce.auth;

import com.darshan.ecommerce.MutableClock;
import com.darshan.ecommerce.auth.RefreshTokenService.Rejected;
import com.darshan.ecommerce.auth.RefreshTokenService.Rotated;
import com.darshan.ecommerce.user.User;
import com.darshan.ecommerce.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant START = Instant.parse("2026-03-15T10:15:30Z");
    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private UserRepository userRepository;

    private final MutableClock clock = new MutableClock(START);
    private final User user = new User("alice@example.com", "hash", "Alice", Set.of());
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", 7L);
        service = new RefreshTokenService(repository, userRepository,
                new JwtProperties("unit-test-secret-0123456789abcdef0123456789", "issuer", Duration.ofMinutes(15), TTL),
                clock);
    }

    @Test
    void issueStoresOnlyTheHashWithANewFamilyAndTheConfiguredExpiry() {
        when(userRepository.getReferenceById(7L)).thenReturn(user);

        String raw = service.issue(7L);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getFamilyId()).hasSize(36);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(START.plus(TTL));
        assertThat(saved.getValue().getUser()).isSameAs(user);
    }

    @Test
    void rotateRetiresTheOldTokenAndIssuesASuccessorInTheSameFamily() {
        RefreshToken current = stored("raw-1", "family-a", START.plus(TTL));
        when(repository.save(any())).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            ReflectionTestUtils.setField(token, "id", 42L);
            return token;
        });
        clock.advance(Duration.ofHours(1));

        RefreshTokenService.RotationResult result = service.rotate("raw-1");

        assertThat(result).isInstanceOfSatisfying(Rotated.class, rotated -> {
            assertThat(rotated.user()).isSameAs(user);
            assertThat(rotated.rawToken()).isNotEqualTo("raw-1");
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getFamilyId()).isEqualTo("family-a");
            assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(rotated.rawToken()));
            assertThat(saved.getValue().getExpiresAt()).isEqualTo(clock.instant().plus(TTL));
        });
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(current.getReplacedById()).isEqualTo(42L);
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void presentingARetiredTokenRevokesTheWholeFamily() {
        RefreshToken reused = stored("raw-old", "family-a", START.plus(TTL));
        reused.revoke(START);
        when(repository.revokeFamily("family-a", clock.instant())).thenReturn(2);

        assertThat(service.rotate("raw-old")).isEqualTo(new Rejected(Rejected.Reason.REUSED));

        verify(repository).revokeFamily("family-a", clock.instant());
        verify(repository, never()).save(any());
    }

    @Test
    void anExpiredTokenIsRejectedWithoutPunishingTheFamily() {
        RefreshToken expired = stored("raw-expired", "family-a", START.plus(TTL));
        clock.advance(TTL);

        assertThat(service.rotate("raw-expired")).isEqualTo(new Rejected(Rejected.Reason.EXPIRED));

        assertThat(expired.isRevoked()).isTrue();
        verify(repository, never()).revokeFamily(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void anUnknownTokenIsRejected() {
        when(repository.findByTokenHash(OpaqueTokens.hash("nope"))).thenReturn(Optional.empty());

        assertThat(service.rotate("nope")).isEqualTo(new Rejected(Rejected.Reason.UNKNOWN));
    }

    @Test
    void revokeOnLogoutRevokesTheFamilyAndIgnoresUnknownTokens() {
        stored("raw-logout", "family-b", START.plus(TTL));
        when(repository.findByTokenHash(OpaqueTokens.hash("unknown"))).thenReturn(Optional.empty());

        service.revoke("raw-logout");
        service.revoke("unknown");

        verify(repository).revokeFamily("family-b", clock.instant());
    }

    private RefreshToken stored(String raw, String family, Instant expiresAt) {
        RefreshToken token = new RefreshToken(user, OpaqueTokens.hash(raw), family, expiresAt);
        when(repository.findByTokenHash(OpaqueTokens.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }
}
