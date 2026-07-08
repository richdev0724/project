package com.marketmono.service.auth;

import com.marketmono.dto.admin.AdminAppUserLifecycleResponse;
import com.marketmono.dto.admin.AdminAppUserHardDeleteChallengeResponse;
import com.marketmono.entity.auth.AdminHardDeleteChallenge;
import com.marketmono.entity.auth.EmailVerificationToken;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.repository.auth.AdminHardDeleteChallengeRepository;
import com.marketmono.repository.auth.EmailVerificationTokenRepository;
import com.marketmono.repository.auth.OAuth2ProviderRepository;
import com.marketmono.repository.auth.PasswordResetTokenRepository;
import com.marketmono.repository.auth.PendingTwoFactorLoginRepository;
import com.marketmono.repository.auth.RecoveryCodeRepository;
import com.marketmono.repository.auth.RefreshTokenRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAppUserLifecycleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PendingTwoFactorLoginRepository pendingTwoFactorLoginRepository;

    private AdminAppUserLifecycleService service;

    @Mock
    private RecoveryCodeRepository recoveryCodeRepository;

    @Mock
    private OAuth2ProviderRepository oAuth2ProviderRepository;

    @Mock
    private AdminHardDeleteChallengeRepository hardDeleteChallengeRepository;

    private MutableClock clock;
    private AtomicReference<AdminHardDeleteChallenge> savedHardDeleteChallenge;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-05T10:00:00Z"));
        savedHardDeleteChallenge = new AtomicReference<>();
        lenient().when(hardDeleteChallengeRepository.save(any(AdminHardDeleteChallenge.class)))
            .thenAnswer(invocation -> {
                AdminHardDeleteChallenge challenge = invocation.getArgument(0);
                savedHardDeleteChallenge.set(challenge);
                return challenge;
            });
        lenient().when(hardDeleteChallengeRepository.findById(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID challengeId = invocation.getArgument(0);
                AdminHardDeleteChallenge challenge = savedHardDeleteChallenge.get();
                return challenge != null && challenge.getId().equals(challengeId)
                    ? Optional.of(challenge)
                    : Optional.empty();
            });
        lenient().when(hardDeleteChallengeRepository.deleteExpiredBefore(any(LocalDateTime.class))).thenReturn(0);
        service = new AdminAppUserLifecycleService(
            userRepository,
            refreshTokenRepository,
            passwordResetTokenRepository,
            emailVerificationTokenRepository,
            pendingTwoFactorLoginRepository,
            recoveryCodeRepository,
            oAuth2ProviderRepository,
            hardDeleteChallengeRepository,
            clock,
            new Random(7)
        );
    }

    @Test
    void softDeleteRetiresUserAndClearsAuthState() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("member@example.com")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .twoFactorEnabled(true)
            .twoFactorSecret("stale-secret")
            .failedLoginAttempts(3)
            .lockedUntil(LocalDateTime.now().plusMinutes(10))
            .build();
        user.setEmail("member@example.com");

        when(userRepository.findActiveByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminAppUserLifecycleResponse response = service.softDeleteUser(userId, actorId, "customer request");

        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getEnabled()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getTwoFactorEnabled()).isFalse();
        assertThat(user.getTwoFactorSecret()).isNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("member@example.com");
        assertThat(response.deleted()).isTrue();
        assertThat(response.restoreEligible()).isFalse();
        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(passwordResetTokenRepository).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(emailVerificationTokenRepository).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(pendingTwoFactorLoginRepository).deleteByUser(user);
    }

    @Test
    void restoreClearsDeletedAtButKeepsAccountDisabledByDefault() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User retired = retiredUser(userId, "retired@example.com");

        when(userRepository.findByIdIncludingDeletedForUpdate(userId)).thenReturn(Optional.of(retired));
        when(userRepository.existsActiveByEmailLowercaseAndIdNot("retired@example.com", userId)).thenReturn(false);
        when(userRepository.save(retired)).thenReturn(retired);

        AdminAppUserLifecycleResponse response = service.restoreUser(userId, actorId, "support review");

        assertThat(retired.getDeletedAt()).isNull();
        assertThat(retired.getEnabled()).isFalse();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.deleted()).isFalse();
        assertThat(response.enabled()).isFalse();
        assertThat(response.restoreEligible()).isFalse();
    }

    @Test
    void restoreRejectsWhenAnotherActiveUserHasSameEmail() {
        UUID userId = UUID.randomUUID();
        User retired = retiredUser(userId, "retired@example.com");

        when(userRepository.findByIdIncludingDeletedForUpdate(userId)).thenReturn(Optional.of(retired));
        when(userRepository.existsActiveByEmailLowercaseAndIdNot("retired@example.com", userId)).thenReturn(true);

        assertThatThrownBy(() -> service.restoreUser(userId, UUID.randomUUID(), "duplicate review"))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getReason()).isEqualTo(
                    "Cannot restore account because another active account already uses this email."
                );
            });

        assertThat(retired.getDeletedAt()).isNotNull();
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
    }

    @Test
    void banEmailDisablesUserAndBlocksNormalizedEmailWithoutRetiringAccount() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("Member@Example.com")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(true)
            .emailVerified(true)
            .twoFactorEnabled(true)
            .twoFactorSecret("stale-secret")
            .failedLoginAttempts(2)
            .lockedUntil(LocalDateTime.now().plusMinutes(10))
            .build();
        user.setEmail("Member@Example.com");

        when(userRepository.findByIdIncludingDeletedForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminAppUserLifecycleResponse response = service.banEmail(userId, actorId, "abuse escalation");

        assertThat(user.getBannedAt()).isEqualTo(LocalDateTime.of(2026, 6, 5, 10, 0));
        assertThat(user.getBannedEmailLowercase()).isEqualTo("member@example.com");
        assertThat(user.getBannedByAdminId()).isEqualTo(actorId);
        assertThat(user.getBannedReason()).isEqualTo("abuse escalation");
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getEnabled()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getTwoFactorEnabled()).isFalse();
        assertThat(user.getTwoFactorSecret()).isNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.banned()).isTrue();
        assertThat(response.bannedAt()).isEqualTo(LocalDateTime.of(2026, 6, 5, 10, 0));
        assertThat(response.deleted()).isFalse();
        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(passwordResetTokenRepository).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(emailVerificationTokenRepository).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(pendingTwoFactorLoginRepository).deleteByUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void unbanEmailClearsBanMetadataButKeepsAccountDisabled() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("member@example.com")
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(false)
            .emailVerified(true)
            .bannedAt(LocalDateTime.now().minusDays(1))
            .bannedEmailLowercase("member@example.com")
            .bannedByAdminId(UUID.randomUUID())
            .bannedReason("old reason")
            .build();
        user.setEmail("member@example.com");

        when(userRepository.findByIdIncludingDeletedForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminAppUserLifecycleResponse response = service.unbanEmail(userId, actorId, "appeal approved");

        assertThat(user.getBannedAt()).isNull();
        assertThat(user.getBannedEmailLowercase()).isNull();
        assertThat(user.getBannedByAdminId()).isNull();
        assertThat(user.getBannedReason()).isNull();
        assertThat(user.getEnabled()).isFalse();
        assertThat(response.banned()).isFalse();
        assertThat(response.enabled()).isFalse();
        verify(refreshTokenRepository, never()).revokeAllByUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void hardDeleteRejectsBeforeServerDelayAndRequiresExactChallengeCase() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = retiredUser(userId, "retired@example.com");

        when(userRepository.findByIdIncludingDeletedForUpdate(userId)).thenReturn(Optional.of(user));

        AdminAppUserHardDeleteChallengeResponse challenge = service.createHardDeleteChallenge(
            userId,
            actorId,
            "erasure request"
        );

        assertThat(challenge.challengeText()).hasSize(6);
        assertThat(challenge.challengeText().chars().anyMatch(Character::isUpperCase)).isTrue();
        assertThat(challenge.challengeText().chars().anyMatch(Character::isLowerCase)).isTrue();
        assertThat(challenge.notBefore()).isEqualTo(LocalDateTime.of(2026, 6, 5, 10, 0, 30));

        assertThatThrownBy(() -> service.hardDeleteUser(
            userId,
            actorId,
            challenge.challengeId(),
            challenge.challengeText(),
            "too early"
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
        );

        clock.advanceSeconds(30);

        assertThatThrownBy(() -> service.hardDeleteUser(
            userId,
            actorId,
            challenge.challengeId(),
            challenge.challengeText().toLowerCase(),
            "wrong case"
        )).isInstanceOfSatisfying(ResponseStatusException.class, ex ->
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void hardDeleteAnonymizesRetainedUserAndReleasesEmail() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = retiredUser(userId, "retired@example.com");
        user.setPasswordHash("hash");
        user.setDisplayName("Retired Member");
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("secret");

        when(userRepository.findByIdIncludingDeletedForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminAppUserHardDeleteChallengeResponse challenge = service.createHardDeleteChallenge(
            userId,
            actorId,
            "erasure request"
        );
        clock.advanceSeconds(30);

        AdminAppUserLifecycleResponse response = service.hardDeleteUser(
            userId,
            actorId,
            challenge.challengeId(),
            challenge.challengeText(),
            "erasure approved"
        );

        assertThat(user.getEmail()).startsWith("deleted-" + userId + "@");
        assertThat(user.getEmail()).isNotEqualTo("retired@example.com");
        assertThat(user.getEmailLowercase()).isEqualTo(user.getEmail());
        assertThat(user.getDisplayName()).isEqualTo("Deleted User");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getEnabled()).isFalse();
        assertThat(user.getEmailVerified()).isFalse();
        assertThat(user.getTwoFactorEnabled()).isFalse();
        assertThat(user.getTwoFactorSecret()).isNull();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.restoreBlockedReason()).isEqualTo("Account has been anonymized.");
        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(passwordResetTokenRepository).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(emailVerificationTokenRepository).invalidateAllByUser(any(User.class), any(LocalDateTime.class));
        verify(pendingTwoFactorLoginRepository).deleteByUser(user);
        verify(recoveryCodeRepository).deleteByUser(user);
        verify(oAuth2ProviderRepository).deleteByUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void hardDeleteChallengeCanBeCompletedByAnotherServiceInstance() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = retiredUser(userId, "retired@example.com");
        user.setPasswordHash("hash");

        when(userRepository.findByIdIncludingDeletedForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminAppUserHardDeleteChallengeResponse challenge = service.createHardDeleteChallenge(
            userId,
            actorId,
            "erasure request"
        );

        AdminAppUserLifecycleService otherInstance = new AdminAppUserLifecycleService(
            userRepository,
            refreshTokenRepository,
            passwordResetTokenRepository,
            emailVerificationTokenRepository,
            pendingTwoFactorLoginRepository,
            recoveryCodeRepository,
            oAuth2ProviderRepository,
            hardDeleteChallengeRepository,
            clock,
            new Random(11)
        );
        clock.advanceSeconds(30);

        AdminAppUserLifecycleResponse response = otherInstance.hardDeleteUser(
            userId,
            actorId,
            challenge.challengeId(),
            challenge.challengeText(),
            "erasure approved"
        );

        assertThat(response.restoreBlockedReason()).isEqualTo("Account has been anonymized.");
        assertThat(user.getEmail()).startsWith("deleted-" + userId + "@");
        verify(hardDeleteChallengeRepository).deleteById(challenge.challengeId());
    }

    private User retiredUser(UUID userId, String email) {
        User user = User.builder()
            .id(userId)
            .email(email)
            .registrationStatus(UserRegistrationStatus.REGISTERED)
            .enabled(false)
            .emailVerified(true)
            .deletedAt(LocalDateTime.now().minusDays(2))
            .build();
        user.setEmail(email);
        return user;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
