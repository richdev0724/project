package com.marketmono.dto;

import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.StripeMode;

import java.util.Map;

/**
 * Stripe configuration DTO with masked keys for security.
 * Used for admin panel display and API responses.
 */
public record StripeConfigDto(
    // Test Mode Keys (masked)
    String testPublicKey,
    String testSecretKey,
    String testWebhookSecret,
    
    // Production Mode Keys (masked)
    String prodPublicKey,
    String prodSecretKey,
    String prodWebhookSecret,
    
    // Default mode
    StripeMode defaultMode,
    
    // Subscription settings
    Integer pastDueGracePeriodDays,
    String betaPromoCode,
    String betaPromotionCodeId,
    Integer betaTrialDays,
    
    // Status flags
    Boolean hasTestKeysConfigured,
    Boolean hasProdKeysConfigured
) {
    public StripeConfigDto(
            String testPublicKey,
            String testSecretKey,
            String testWebhookSecret,
            String prodPublicKey,
            String prodSecretKey,
            String prodWebhookSecret,
            StripeMode defaultMode,
            Integer pastDueGracePeriodDays,
            Boolean hasTestKeysConfigured,
            Boolean hasProdKeysConfigured) {
        this(
            testPublicKey,
            testSecretKey,
            testWebhookSecret,
            prodPublicKey,
            prodSecretKey,
            prodWebhookSecret,
            defaultMode,
            pastDueGracePeriodDays,
            "BETAMM2026",
            StripeConfig.maskKey(null),
            3,
            hasTestKeysConfigured,
            hasProdKeysConfigured
        );
    }

    /**
     * Create DTO from config map with optional unmasking of public keys.
     */
    public static StripeConfigDto fromMap(Map<String, String> configMap, boolean showPublicKeys) {
        String testPubKey = configMap.get(StripeConfig.TEST_PUBLIC_KEY);
        String testSecKey = configMap.get(StripeConfig.TEST_SECRET_KEY);
        String testWebhook = configMap.get(StripeConfig.TEST_WEBHOOK_SECRET);
        String prodPubKey = configMap.get(StripeConfig.PROD_PUBLIC_KEY);
        String prodSecKey = configMap.get(StripeConfig.PROD_SECRET_KEY);
        String prodWebhook = configMap.get(StripeConfig.PROD_WEBHOOK_SECRET);
        String defaultMode = configMap.get(StripeConfig.DEFAULT_MODE);
        String gracePeriod = configMap.get(StripeConfig.PAST_DUE_GRACE_PERIOD_DAYS);
        String betaPromoCode = configMap.get(StripeConfig.BETA_PROMO_CODE);
        String betaPromotionCodeId = configMap.get(StripeConfig.BETA_PROMOTION_CODE_ID);
        String betaTrialDays = configMap.get(StripeConfig.BETA_TRIAL_DAYS);
        
        return new StripeConfigDto(
            showPublicKeys ? testPubKey : StripeConfig.maskKey(testPubKey),
            StripeConfig.maskKey(testSecKey),
            StripeConfig.maskKey(testWebhook),
            showPublicKeys ? prodPubKey : StripeConfig.maskKey(prodPubKey),
            StripeConfig.maskKey(prodSecKey),
            StripeConfig.maskKey(prodWebhook),
            parseStripeMode(defaultMode),
            parseInteger(gracePeriod, 1),
            valueOrDefault(betaPromoCode, "BETAMM2026"),
            StripeConfig.maskKey(betaPromotionCodeId),
            parseInteger(betaTrialDays, 3),
            hasKey(testPubKey) && hasKey(testSecKey),
            hasKey(prodPubKey) && hasKey(prodSecKey)
        );
    }
    
    private static StripeMode parseStripeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return StripeMode.TEST;
        }
        try {
            return StripeMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return StripeMode.TEST;
        }
    }
    
    private static Integer parseInteger(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
