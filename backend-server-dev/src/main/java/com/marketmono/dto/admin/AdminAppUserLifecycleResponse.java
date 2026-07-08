package com.marketmono.dto.admin;

import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRegistrationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminAppUserLifecycleResponse(
    UUID userId,
    String email,
    String displayName,
    UserRegistrationStatus registrationStatus,
    boolean enabled,
    boolean emailVerified,
    boolean deleted,
    boolean banned,
    LocalDateTime createdAt,
    LocalDateTime deletedAt,
    LocalDateTime bannedAt,
    String bannedReason,
    UUID bannedByAdminId,
    boolean restoreEligible,
    String restoreBlockedReason
) {
    public static AdminAppUserLifecycleResponse fromUser(
            User user,
            boolean restoreEligible,
            String restoreBlockedReason) {
        return new AdminAppUserLifecycleResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRegistrationStatus(),
            Boolean.TRUE.equals(user.getEnabled()),
            Boolean.TRUE.equals(user.getEmailVerified()),
            user.isDeleted(),
            user.isBanned(),
            user.getCreatedAt(),
            user.getDeletedAt(),
            user.getBannedAt(),
            user.getBannedReason(),
            user.getBannedByAdminId(),
            restoreEligible,
            restoreBlockedReason
        );
    }
}
