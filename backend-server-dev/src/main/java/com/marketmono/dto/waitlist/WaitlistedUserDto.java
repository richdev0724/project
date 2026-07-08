package com.marketmono.dto.waitlist;

import com.marketmono.entity.auth.UserRegistrationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record WaitlistedUserDto(
    UUID id,
    String email,
    String displayName,
    UserRegistrationStatus registrationStatus,
    LocalDateTime waitlistJoinedAt,
    String waitlistSource,
    LocalDateTime waitlistConfirmationEmailSentAt,
    LocalDateTime createdAt,
    Boolean betaUser,
    LocalDateTime betaInvitedAt,
    LocalDateTime betaSignedUpAt,
    LocalDateTime betaInviteLastSentAt,
    UUID betaInvitedBy,
    BetaWaitlistInviteStatus betaInviteStatus,
    Boolean usedBetaPromo,
    LocalDateTime betaPromoAppliedAt
) {
    public WaitlistedUserDto(
            UUID id,
            String email,
            String displayName,
            UserRegistrationStatus registrationStatus,
            LocalDateTime waitlistJoinedAt,
            String waitlistSource,
            LocalDateTime waitlistConfirmationEmailSentAt,
            LocalDateTime createdAt) {
        this(
            id,
            email,
            displayName,
            registrationStatus,
            waitlistJoinedAt,
            waitlistSource,
            waitlistConfirmationEmailSentAt,
            createdAt,
            false,
            null,
            null,
            null,
            null,
            BetaWaitlistInviteStatus.NOT_INVITED,
            false,
            null
        );
    }
}
