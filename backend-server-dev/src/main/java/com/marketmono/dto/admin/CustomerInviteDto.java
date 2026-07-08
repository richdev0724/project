package com.marketmono.dto.admin;

import com.marketmono.entity.auth.CustomerInviteStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerInviteDto(
    UUID id,
    String email,
    String displayName,
    String registrationStatus,
    CustomerInviteStatus inviteStatus,
    boolean enabled,
    boolean emailVerified,
    boolean requiresPasswordChange,
    LocalDateTime customerInvitedAt,
    LocalDateTime customerInviteLastSentAt,
    LocalDateTime tempPasswordExpiresAt,
    LocalDateTime customerInviteRevokedAt,
    LocalDateTime passwordChangedAt
) {
}
