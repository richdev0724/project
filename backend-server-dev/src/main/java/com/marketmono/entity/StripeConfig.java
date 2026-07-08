package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stripe configuration entity for storing key-value settings.
 * Each row represents a single configuration setting.
 * 
 * Config names:
 * - test_public_key, test_secret_key, test_webhook_secret
 * - prod_public_key, prod_secret_key, prod_webhook_secret
 * - default_mode (TEST/PROD)
 * - past_due_grace_period_days
 * - registration_mode (WAITLIST/OPEN/CLOSED)
 * - subscriber_cap_enabled
 * - subscriber_cap_limit
 * - beta_promo_code
 * - beta_promotion_code_id
 * - beta_trial_days
 */
@Entity
@Table(name = "stripe_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class StripeConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Configuration setting name (unique key).
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
    
    /**
     * Configuration value (can be null for unset configs).
     */
    @Column(name = "value", columnDefinition = "TEXT")
    private String value;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ============================================
    // Static Config Names
    // ============================================
    
    public static final String TEST_PUBLIC_KEY = "test_public_key";
    public static final String TEST_SECRET_KEY = "test_secret_key";
    public static final String TEST_WEBHOOK_SECRET = "test_webhook_secret";
    public static final String PROD_PUBLIC_KEY = "prod_public_key";
    public static final String PROD_SECRET_KEY = "prod_secret_key";
    public static final String PROD_WEBHOOK_SECRET = "prod_webhook_secret";
    public static final String DEFAULT_MODE = "default_mode";
    public static final String PAST_DUE_GRACE_PERIOD_DAYS = "past_due_grace_period_days";
    public static final String REGISTRATION_MODE = "registration_mode";
    public static final String SUBSCRIBER_CAP_ENABLED = "subscriber_cap_enabled";
    public static final String SUBSCRIBER_CAP_LIMIT = "subscriber_cap_limit";
    public static final String BETA_PROMO_CODE = "beta_promo_code";
    public static final String BETA_PROMOTION_CODE_ID = "beta_promotion_code_id";
    public static final String BETA_TRIAL_DAYS = "beta_trial_days";
    public static final String HONOR_COMPLIMENTARY_PRODUCT_ID = "honor_complimentary_product_id";
    public static final String HONOR_GRANT_DURATION_DAYS = "honor_grant_duration_days";
    
    // ============================================
    // Helper Methods
    // ============================================
    
    /**
     * Check if this config has a value set.
     */
    public boolean hasValue() {
        return value != null && !value.isBlank();
    }
    
    /**
     * Get value as integer, with default.
     */
    public int getValueAsInt(int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Mask a key for display (show first 7 and last 4 characters).
     * e.g., "sk_test_****abcd"
     */
    public static String maskKey(String key) {
        if (key == null || key.length() < 12) {
            return "****";
        }
        return key.substring(0, 7) + "****" + key.substring(key.length() - 4);
    }
    
    /**
     * Get masked value for display.
     */
    public String getMaskedValue() {
        return maskKey(value);
    }
}
