package com.marketmono.service.auth;

import com.marketmono.dto.admin.AdminAppUserHardDeleteChallengeResponse;
import com.marketmono.dto.admin.AdminAppUserLifecycleResponse;
import com.marketmono.entity.auth.AdminHardDeleteChallenge;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
public class AdminAppUserLifecycleService {

    private static final String RESTORE_EMAIL_CONFLICT_REASON =
        "Cannot restore account because another active account already uses this email.";
    private static final Duration HARD_DELETE_DELAY = Duration.ofSeconds(30);
    private static final Duration HARD_DELETE_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final String HARD_DELETE_WORD = "DELETE";
    private static final String ANONYMIZED_EMAIL_DOMAIN = "deleted.marketmono.local";
    private static final String ANONYMIZED_DISPLAY_NAME = "Deleted User";
    private static final String HARD_DELETE_COMPLETED_MESSAGE = "Account has been anonymized.";
    private static final int MAX_BAN_REASON_LENGTH = 500;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PendingTwoFactorLoginRepository pendingTwoFactorLoginRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final OAuth2ProviderRepository oAuth2ProviderRepository;
    private final AdminHardDeleteChallengeRepository hardDeleteChallengeRepository;
    private final Clock clock;
    private final Random random;

    @Autowired
    public AdminAppUserLifecycleService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PendingTwoFactorLoginRepository pendingTwoFactorLoginRepository,
            RecoveryCodeRepository recoveryCodeRepository,
            OAuth2ProviderRepository oAuth2ProviderRepository,
            AdminHardDeleteChallengeRepository hardDeleteChallengeRepository) {
        this(
            userRepository,
            refreshTokenRepository,
            passwordResetTokenRepository,
            emailVerificationTokenRepository,
            pendingTwoFactorLoginRepository,
            recoveryCodeRepository,
            oAuth2ProviderRepository,
            hardDeleteChallengeRepository,
            Clock.systemUTC(),
            new SecureRandom()
        );
    }

    AdminAppUserLifecycleService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PendingTwoFactorLoginRepository pendingTwoFactorLoginRepository,
            RecoveryCodeRepository recoveryCodeRepository,
            OAuth2ProviderRepository oAuth2ProviderRepository,
            AdminHardDeleteChallengeRepository hardDeleteChallengeRepository,
            Clock clock,
            Random random) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.pendingTwoFactorLoginRepository = pendingTwoFactorLoginRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.oAuth2ProviderRepository = oAuth2ProviderRepository;
        this.hardDeleteChallengeRepository = hardDeleteChallengeRepository;
        this.clock = clock;
        this.random = random;
    }

    @Transactional
    public AdminAppUserLifecycleResponse softDeleteUser(UUID userId, UUID actorId, String reason) {
        requireActor(actorId);
        User user = userRepository.findActiveByIdForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active user not found."));
        if (user.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Waitlisted users cannot be retired as app users.");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setDeletedAt(now);
        user.setEnabled(false);
        clearAuthState(user);
        invalidateOutstandingAuthArtifacts(user, now);

        User saved = userRepository.save(user);
        log.info("Admin {} soft-deleted app user {}. reason={}", actorId, userId, reason);
        return AdminAppUserLifecycleResponse.fromUser(saved, false, "Account is retired.");
    }

    @Transactional
    public AdminAppUserLifecycleResponse restoreUser(UUID userId, UUID actorId, String reason) {
        requireActor(actorId);
        User user = userRepository.findByIdIncludingDeletedForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (!user.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not retired.");
        }
        if (userRepository.existsActiveByEmailLowercaseAndIdNot(user.getEmailLowercase(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, RESTORE_EMAIL_CONFLICT_REASON);
        }

        user.setDeletedAt(null);
        user.setEnabled(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        User saved = userRepository.save(user);
        log.info("Admin {} restored retired app user {}. reason={}", actorId, userId, reason);
        return AdminAppUserLifecycleResponse.fromUser(saved, false, "Account is active.");
    }

    @Transactional
    public AdminAppUserLifecycleResponse banEmail(UUID userId, UUID actorId, String reason) {
        requireActor(actorId);
        User user = userRepository.findByIdIncludingDeletedForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        LocalDateTime now = now();
        user.setBannedAt(now);
        user.setBannedEmailLowercase(normalizedEmailLowercase(user));
        user.setBannedReason(normalizeReason(reason));
        user.setBannedByAdminId(actorId);
        user.setEnabled(false);
        clearAuthState(user);
        invalidateOutstandingAuthArtifacts(user, now);

        User saved = userRepository.save(user);
        log.info("Admin {} banned app user email for user {}. reason={}", actorId, userId, reason);
        return AdminAppUserLifecycleResponse.fromUser(saved, !saved.isDeleted(), "Email is banned.");
    }

    @Transactional
    public AdminAppUserLifecycleResponse unbanEmail(UUID userId, UUID actorId, String reason) {
        requireActor(actorId);
        User user = userRepository.findByIdIncludingDeletedForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        user.setBannedAt(null);
        user.setBannedEmailLowercase(null);
        user.setBannedReason(null);
        user.setBannedByAdminId(null);
        user.setEnabled(false);

        User saved = userRepository.save(user);
        log.info("Admin {} unbanned app user email for user {}. reason={}", actorId, userId, reason);
        return AdminAppUserLifecycleResponse.fromUser(saved, !saved.isDeleted(), "Email ban has been cleared.");
    }

    @Transactional
    public AdminAppUserHardDeleteChallengeResponse createHardDeleteChallenge(UUID userId, UUID actorId, String reason) {
        requireActor(actorId);
        User user = userRepository.findByIdIncludingDeletedForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        LocalDateTime now = now();
        LocalDateTime notBefore = now.plus(HARD_DELETE_DELAY);
        LocalDateTime expiresAt = now.plus(HARD_DELETE_CHALLENGE_TTL);
        UUID challengeId = UUID.randomUUID();
        String challengeText = randomCaseDelete();
        hardDeleteChallengeRepository.save(AdminHardDeleteChallenge.builder()
            .id(challengeId)
            .userId(user.getId())
            .actorId(actorId)
            .challengeText(challengeText)
            .notBefore(notBefore)
            .expiresAt(expiresAt)
            .createdAt(now)
            .build());
        pruneExpiredChallenges(now);
        log.info("Admin {} created hard-delete challenge for app user {}. reason={}", actorId, userId, reason);
        return new AdminAppUserHardDeleteChallengeResponse(challengeId, challengeText, notBefore, expiresAt);
    }

    @Transactional
    public AdminAppUserLifecycleResponse hardDeleteUser(
            UUID userId,
            UUID actorId,
            UUID challengeId,
            String challengeAnswer,
            String reason) {
        requireActor(actorId);
        AdminHardDeleteChallenge challenge = requireValidHardDeleteChallenge(
            userId,
            actorId,
            challengeId,
            challengeAnswer,
            now()
        );
        User user = userRepository.findByIdIncludingDeletedForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        LocalDateTime now = now();
        clearAuthState(user);
        invalidateOutstandingAuthArtifacts(user, now);
        recoveryCodeRepository.deleteByUser(user);
        oAuth2ProviderRepository.deleteByUser(user);
        anonymizeUser(user, now);
        User saved = userRepository.save(user);
        hardDeleteChallengeRepository.deleteById(challenge.getId());

        log.info("Admin {} hard-deleted/anonymized app user {}. reason={}", actorId, userId, reason);
        return AdminAppUserLifecycleResponse.fromUser(saved, false, HARD_DELETE_COMPLETED_MESSAGE);
    }

    private void requireActor(UUID actorId) {
        if (actorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin actor id is required.");
        }
    }

    private void clearAuthState(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setRequiresPasswordChange(false);
        user.setTempPasswordExpiresAt(null);
        user.setCustomerInviteStatus(null);
        user.setCustomerInvitedAt(null);
        user.setCustomerInviteLastSentAt(null);
        user.setCustomerInviteRevokedAt(null);
        user.setCustomerInvitedBy(null);
        user.setPasswordChangedAt(null);
    }

    private void invalidateOutstandingAuthArtifacts(User user, LocalDateTime now) {
        refreshTokenRepository.revokeAllByUser(user);
        passwordResetTokenRepository.invalidateAllByUser(user, now);
        emailVerificationTokenRepository.invalidateAllByUser(user, now);
        pendingTwoFactorLoginRepository.deleteByUser(user);
    }

    private AdminHardDeleteChallenge requireValidHardDeleteChallenge(
            UUID userId,
            UUID actorId,
            UUID challengeId,
            String challengeAnswer,
            LocalDateTime now) {
        if (challengeId == null || challengeAnswer == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hard-delete challenge id and answer are required.");
        }
        AdminHardDeleteChallenge challenge = hardDeleteChallengeRepository.findById(challengeId).orElse(null);
        if (challenge == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Hard-delete challenge expired.");
        }
        if (now.isAfter(challenge.getExpiresAt())) {
            hardDeleteChallengeRepository.deleteById(challengeId);
            throw new ResponseStatusException(HttpStatus.GONE, "Hard-delete challenge expired.");
        }
        if (!challenge.getUserId().equals(userId) || !challenge.getActorId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hard-delete challenge does not match this request.");
        }
        if (now.isBefore(challenge.getNotBefore())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hard delete is not available until the safety delay has elapsed.");
        }
        if (!challenge.getChallengeText().equals(challengeAnswer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hard-delete challenge answer does not match.");
        }
        return challenge;
    }

    private void anonymizeUser(User user, LocalDateTime now) {
        String anonymizedEmail = "deleted-" + user.getId() + "@" + ANONYMIZED_EMAIL_DOMAIN;
        user.setEmail(anonymizedEmail);
        user.setDisplayName(ANONYMIZED_DISPLAY_NAME);
        user.setPasswordHash(null);
        user.setEnabled(false);
        user.setEmailVerified(false);
        user.setDeletedAt(now);
        user.setLastLoginAt(null);
        user.setWaitlistSource(null);
        user.setWaitlistConfirmationEmailSentAt(null);
        user.setRequiresPasswordChange(false);
        user.setStripeCustomerIdTest(null);
        user.setStripeCustomerIdProd(null);
        user.setReferralCode(null);
        user.setPartnerUser(false);
        user.setBetaUser(false);
        user.getRoles().clear();
    }

    private String normalizedEmailLowercase(User user) {
        String email = user.getEmailLowercase() != null ? user.getEmailLowercase() : user.getEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User email is not available for ban.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= MAX_BAN_REASON_LENGTH
            ? normalized
            : normalized.substring(0, MAX_BAN_REASON_LENGTH);
    }

    private String randomCaseDelete() {
        StringBuilder builder = new StringBuilder(HARD_DELETE_WORD.length());
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        for (char character : HARD_DELETE_WORD.toCharArray()) {
            boolean uppercase = random.nextBoolean();
            builder.append(uppercase ? Character.toUpperCase(character) : Character.toLowerCase(character));
            hasUppercase = hasUppercase || uppercase;
            hasLowercase = hasLowercase || !uppercase;
        }
        if (!hasUppercase || !hasLowercase) {
            int replacementIndex = random.nextInt(builder.length());
            char replacement = builder.charAt(replacementIndex);
            builder.setCharAt(replacementIndex, hasUppercase
                ? Character.toLowerCase(replacement)
                : Character.toUpperCase(replacement));
        }
        return builder.toString();
    }

    private void pruneExpiredChallenges(LocalDateTime now) {
        hardDeleteChallengeRepository.deleteExpiredBefore(now);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
