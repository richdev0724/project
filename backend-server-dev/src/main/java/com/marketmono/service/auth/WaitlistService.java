package com.marketmono.service.auth;

import com.marketmono.dto.waitlist.BulkDeleteWaitlistResponse;
import com.marketmono.dto.waitlist.BetaWaitlistInviteStatus;
import com.marketmono.dto.waitlist.WaitlistRequest;
import com.marketmono.dto.waitlist.WaitlistResponse;
import com.marketmono.dto.waitlist.WaitlistedUserDto;
import com.marketmono.entity.auth.CustomerInviteStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.entity.auth.UserRegistrationStatus;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service for public end-user waitlist capture.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private static final String DEFAULT_WAITLIST_SOURCE = "landing";

    private final UserRepository userRepository;
    private final EmailValidationService emailValidationService;
    private final EmailService emailService;
    private final RegistrationModeService registrationModeService;

    @Transactional
    public WaitlistResponse joinWaitlist(WaitlistRequest request) {
        registrationModeService.requireWaitlistMode();

        String email = normalizeEmail(request.email());
        String displayName = normalizeName(request.name());

        emailValidationService.validateEmail(email);

        if (isEmailBanned(email)) {
            throw new IllegalArgumentException(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);
        }

        User existing = userRepository.findByEmailForUpdate(email).orElse(null);
        if (existing != null) {
            if (existing.getRegistrationStatus() == UserRegistrationStatus.WAITLISTED) {
                existing.setDisplayName(displayName);
                existing.setWaitlistSource(DEFAULT_WAITLIST_SOURCE);
                if (existing.getWaitlistConfirmationEmailSentAt() == null) {
                    sendConfirmationEmailIgnoringFailure(existing);
                    log.info("Retried waitlist confirmation email for: {}", email);
                } else {
                    userRepository.save(existing);
                    log.info("Idempotent waitlist submission for: {}", email);
                }
                return WaitlistResponse.accepted();
            }

            log.info("Waitlist submission received for existing non-waitlisted email: {}", email);
            return WaitlistResponse.alreadyInSystem();
        }

        User user = User.builder()
            .email(email)
            .displayName(displayName)
            .registrationStatus(UserRegistrationStatus.WAITLISTED)
            .enabled(false)
            .emailVerified(false)
            .waitlistJoinedAt(LocalDateTime.now())
            .waitlistSource(DEFAULT_WAITLIST_SOURCE)
            .build();
        user.setEmail(email);

        user = userRepository.save(user);
        sendConfirmationEmailIgnoringFailure(user);

        log.info("Waitlist signup created for: {}", email);
        return WaitlistResponse.accepted();
    }

    @Transactional
    public BulkDeleteWaitlistResponse deleteWaitlistedUsers(List<UUID> ids) {
        List<UUID> uniqueIds = ids == null ? List.of() : new LinkedHashSet<>(ids).stream().toList();
        if (uniqueIds.isEmpty()) {
            return new BulkDeleteWaitlistResponse(0, List.of());
        }

        List<User> waitlistedUsers = userRepository.findActiveWaitlistedByIdInForUpdate(uniqueIds);
        List<WaitlistedUserDto> deletedUsers = waitlistedUsers.stream()
            .map(this::toWaitlistedUserDto)
            .toList();

        if (!waitlistedUsers.isEmpty()) {
            userRepository.deleteAll(waitlistedUsers);
            log.info("Hard-deleted {} waitlisted users from admin request", waitlistedUsers.size());
        }

        return new BulkDeleteWaitlistResponse(deletedUsers.size(), deletedUsers);
    }

    @Transactional
    public WaitlistedUserDto resendWaitlistConfirmation(UUID userId) {
        User user = userRepository.findActiveWaitlistedByIdForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Active waitlisted user not found: " + userId
            ));

        try {
            return toWaitlistedUserDto(sendConfirmationEmailOrThrow(user));
        } catch (RuntimeException e) {
            log.warn("Admin waitlist confirmation resend failed for {}: {}", user.getEmail(), e.getMessage());
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Waitlist confirmation email delivery failed",
                e
            );
        }
    }

    private void sendConfirmationEmailIgnoringFailure(User user) {
        try {
            sendConfirmationEmailOrThrow(user);
        } catch (RuntimeException e) {
            log.warn("Waitlist confirmation email failed for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private User sendConfirmationEmailOrThrow(User user) {
        emailService.sendWaitlistConfirmationEmail(user);
        user.setWaitlistConfirmationEmailSentAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private WaitlistedUserDto toWaitlistedUserDto(User user) {
        UserBetaAccess betaAccess = user.getBetaAccess();
        return new WaitlistedUserDto(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRegistrationStatus(),
            user.getWaitlistJoinedAt(),
            user.getWaitlistSource(),
            user.getWaitlistConfirmationEmailSentAt(),
            user.getCreatedAt(),
            Boolean.TRUE.equals(user.getBetaUser()),
            betaAccess != null ? betaAccess.getBetaInvitedAt() : null,
            betaAccess != null ? betaAccess.getBetaSignedUpAt() : null,
            betaAccess != null ? betaAccess.getBetaInviteLastSentAt() : null,
            betaAccess != null ? betaAccess.getBetaInvitedBy() : null,
            betaInviteStatus(user),
            betaAccess != null && Boolean.TRUE.equals(betaAccess.getUsedBetaPromo()),
            betaAccess != null ? betaAccess.getBetaPromoAppliedAt() : null
        );
    }

    private BetaWaitlistInviteStatus betaInviteStatus(User user) {
        UserBetaAccess betaAccess = user.getBetaAccess();
        LocalDateTime betaInvitedAt = betaAccess != null ? betaAccess.getBetaInvitedAt() : null;
        LocalDateTime betaSignedUpAt = betaAccess != null ? betaAccess.getBetaSignedUpAt() : null;
        LocalDateTime betaInviteLastSentAt = betaAccess != null ? betaAccess.getBetaInviteLastSentAt() : null;
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.REVOKED && betaInvitedAt != null) {
            return BetaWaitlistInviteStatus.REVOKED;
        }
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.EXPIRED && betaInvitedAt != null) {
            return BetaWaitlistInviteStatus.EXPIRED;
        }
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.ACCEPTED || user.getPasswordChangedAt() != null) {
            return BetaWaitlistInviteStatus.ACCEPTED;
        }
        if (betaSignedUpAt != null) {
            return BetaWaitlistInviteStatus.SIGNED_UP_FOR_BETA;
        }
        if (betaInvitedAt != null || betaInviteLastSentAt != null) {
            return BetaWaitlistInviteStatus.BETA_INVITE_SENT;
        }
        return BetaWaitlistInviteStatus.NOT_INVITED;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim();
    }

    public void rejectIfEmailBanned(String email) {
        if (isEmailBanned(email)) {
            throw new IllegalArgumentException(AuthFailureMessages.BANNED_EMAIL_SUPPORT_MESSAGE);
        }
    }

    private boolean isEmailBanned(String email) {
        String normalized = normalizeEmail(email);
        return normalized != null
            && userRepository.existsBannedByEmailLowercase(normalized.toLowerCase(Locale.ROOT));
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim().replaceAll("\\s+", " ");
    }
}
