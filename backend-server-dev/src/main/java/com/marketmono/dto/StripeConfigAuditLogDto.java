package com.marketmono.dto;

import com.marketmono.entity.StripeConfigAuditLog;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stripe configuration audit log DTO.
 * Used for admin security review.
 * Matches database schema from 015-stripe-config-tables.yaml
 * (admin_email removed in 022-refactor-stripe-config-to-kv.yaml - use admin_id FK instead)
 */
public record StripeConfigAuditLogDto(
    UUID id,
    
    // Admin info (join with admin_users for email if needed)
    UUID adminId,
    
    // Change tracking
    String fieldChanged,
    String oldValueMasked,
    String newValueMasked,
    
    // Audit info
    LocalDateTime changedAt,
    String ipAddress,
    String changeDescription,
    boolean isSecretKeyChange
) {
    /**
     * Create DTO from entity.
     */
    public static StripeConfigAuditLogDto from(StripeConfigAuditLog log) {
        return new StripeConfigAuditLogDto(
            log.getId(),
            log.getAdminId(),
            log.getFieldChanged(),
            log.getOldValueMasked(),
            log.getNewValueMasked(),
            log.getChangedAt(),
            log.getIpAddress(),
            log.getChangeDescription(),
            log.isSecretKeyChange()
        );
    }
}
