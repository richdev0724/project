package com.marketmono.dto;

import com.marketmono.entity.BillingPeriod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Internal admin request for granting local subscription access.
 */
public record AdminGrantSubscriptionRequest(
    @NotNull(message = "Product ID is required")
    UUID productId,

    @NotNull(message = "Billing period is required")
    BillingPeriod billingPeriod,

    @Min(value = 1, message = "Duration must be at least 1 day")
    @Max(value = 3660, message = "Duration must be at most 3660 days")
    Integer durationDays,

    @Size(max = 500, message = "Reason must be at most 500 characters")
    String reason
) {
}
