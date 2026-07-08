package com.marketmono.dto;

import com.marketmono.entity.StripeMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating Stripe configuration.
 * Only include fields that are being changed.
 */
public record UpdateStripeConfigRequest(
    @Size(max = 255, message = "Test public key must be at most 255 characters")
    @Pattern(regexp = "^pk_test_.*|^$", message = "Test public key must start with pk_test_")
    String testPublicKey,
    
    @Size(max = 255, message = "Test secret key must be at most 255 characters")
    @Pattern(regexp = "^sk_test_.*|^rk_test_.*|^$", message = "Test secret key must start with sk_test_ or rk_test_")
    String testSecretKey,
    
    @Size(max = 255, message = "Test webhook secret must be at most 255 characters")
    @Pattern(regexp = "^whsec_.*|^$", message = "Webhook secret must start with whsec_")
    String testWebhookSecret,
    
    @Size(max = 255, message = "Production public key must be at most 255 characters")
    @Pattern(regexp = "^pk_live_.*|^$", message = "Production public key must start with pk_live_")
    String prodPublicKey,
    
    @Size(max = 255, message = "Production secret key must be at most 255 characters")
    @Pattern(regexp = "^sk_live_.*|^rk_live_.*|^$", message = "Production secret key must start with sk_live_ or rk_live_")
    String prodSecretKey,
    
    @Size(max = 255, message = "Production webhook secret must be at most 255 characters")
    @Pattern(regexp = "^whsec_.*|^$", message = "Webhook secret must start with whsec_")
    String prodWebhookSecret,
    
    StripeMode defaultMode

    ,
    @Min(value = 0, message = "Grace period days must be 0 or greater")
    Integer pastDueGracePeriodDays,

    @Size(max = 40, message = "Beta promo code must be at most 40 characters")
    String betaPromoCode,

    @Size(max = 255, message = "Beta promotion code ID must be at most 255 characters")
    @Pattern(regexp = "^promo_.*|^$", message = "Beta promotion code ID must start with promo_")
    String betaPromotionCodeId,

    @Min(value = 0, message = "Beta trial days must be 0 or greater")
    Integer betaTrialDays
) {
    public UpdateStripeConfigRequest(
            String testPublicKey,
            String testSecretKey,
            String testWebhookSecret,
            String prodPublicKey,
            String prodSecretKey,
            String prodWebhookSecret,
            StripeMode defaultMode,
            Integer pastDueGracePeriodDays) {
        this(
            testPublicKey,
            testSecretKey,
            testWebhookSecret,
            prodPublicKey,
            prodSecretKey,
            prodWebhookSecret,
            defaultMode,
            pastDueGracePeriodDays,
            null,
            null,
            null
        );
    }
}
