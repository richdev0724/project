package com.marketmono.dto;

import com.marketmono.entity.BillingPeriod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for downgrading a subscription to a lower product.
 * Downgrades are scheduled to take effect at the end of the current billing period.
 */
public record DowngradeSubscriptionRequest(
    @NotNull(message = "New product ID is required")
    UUID newProductId,

    // Optional - keeps current billing period if not specified
    BillingPeriod billingPeriod,
    
    @Size(max = 500, message = "Reason must be at most 500 characters")
    String reason
) {}
