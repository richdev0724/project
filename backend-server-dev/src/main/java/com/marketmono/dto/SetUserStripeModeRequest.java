package com.marketmono.dto;

import com.marketmono.entity.StripeMode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for setting a user's Stripe mode override.
 * Used by admins to set individual users to test or production mode.
 */
public record SetUserStripeModeRequest(
    @NotNull(message = "User ID is required")
    UUID userId,
    
    @NotNull(message = "Stripe mode is required")
    StripeMode stripeMode
) {}
