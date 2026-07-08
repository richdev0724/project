package com.marketmono.dto;

import com.marketmono.entity.BillingPeriod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new subscription.
 */
public record CreateSubscriptionRequest(
    @NotNull(message = "Product ID is required")
    UUID productId,
    
    @NotNull(message = "Billing period is required")
    BillingPeriod billingPeriod,
    
    // Optional Stripe payment method (for immediate charge)
    String paymentMethodId
) {}
