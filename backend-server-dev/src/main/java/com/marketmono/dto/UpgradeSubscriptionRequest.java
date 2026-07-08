package com.marketmono.dto;

import com.marketmono.entity.BillingPeriod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for upgrading a subscription to a new product.
 * Upgrades are applied immediately with proration.
 */
public record UpgradeSubscriptionRequest(
    @NotNull(message = "New product ID is required")
    UUID newProductId,
    
    // Optional - keeps current billing period if not specified
    BillingPeriod billingPeriod
) {}
