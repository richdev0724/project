package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log for Stripe configuration changes.
 * Tracks all modifications to configuration values.
 * 
 * NOTE: Matches database schema from 015-stripe-config-tables.yaml
 * Updated in 022-refactor-stripe-config-to-kv.yaml to remove redundant admin_email column.
 * Use admin_id FK to join with admin_users table for email lookups.
 */
@Entity
@Table(name = "stripe_config_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class StripeConfigAuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    // ============================================
    // Admin Info (who made the change)
    // ============================================
    
    /**
     * Reference to admin user who made the change.
     * Join with admin_users table to get email when needed.
     */
    @Column(name = "admin_id", nullable = false)
    private UUID adminId;
    
    // ============================================
    // Change Tracking
    // ============================================
    
    /**
     * The field/configuration key that was changed.
     * Maps to stripe_config.name in the new KV structure.
     */
    @Column(name = "field_changed", nullable = false, length = 50)
    private String fieldChanged;
    
    @Column(name = "old_value_masked", length = 50)
    private String oldValueMasked;
    
    @Column(name = "new_value_masked", length = 50)
    private String newValueMasked;
    
    // ============================================
    // Audit Info
    // ============================================
    
    @CreatedDate
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    // ============================================
    // Factory Methods
    // ============================================
    
    /**
     * Create an audit log entry for a field change.
     * Values are automatically masked for security.
     */
    public static StripeConfigAuditLog createEntry(
            String fieldName,
            String oldValue,
            String newValue,
            UUID adminId,
            String ipAddress) {
        return StripeConfigAuditLog.builder()
                .fieldChanged(fieldName)
                .oldValueMasked(maskValueForAudit(oldValue))
                .newValueMasked(maskValueForAudit(newValue))
                .adminId(adminId)
                .ipAddress(ipAddress)
                .build();
    }
    
    /**
     * Mask sensitive key values for audit log storage.
     * Shows first 7 and last 4 characters.
     */
    private static String maskValueForAudit(String value) {
        if (value == null || value.isBlank()) {
            return "[empty]";
        }
        if (value.length() < 12) {
            return "****";
        }
        // Truncate to fit 50 char limit: first 7 + 4 (****) + last 4 = 15 chars normally
        String masked = value.substring(0, 7) + "****" + value.substring(value.length() - 4);
        return masked.length() > 50 ? masked.substring(0, 50) : masked;
    }
    
    // ============================================
    // Helper Methods
    // ============================================
    
    /**
     * Get a friendly description of the change.
     */
    public String getChangeDescription() {
        return String.format("Changed %s from %s to %s", fieldChanged, oldValueMasked, newValueMasked);
    }
    
    /**
     * Check if this change was for a secret key.
     */
    public boolean isSecretKeyChange() {
        return fieldChanged != null && fieldChanged.toLowerCase().contains("secret");
    }
}
