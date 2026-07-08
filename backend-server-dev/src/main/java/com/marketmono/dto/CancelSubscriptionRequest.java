package com.marketmono.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for canceling a subscription.
 */
public record CancelSubscriptionRequest(
    @Size(max = 500, message = "Reason must be at most 500 characters")
    String reason,
    
    // If true, cancel immediately. If false (default), cancel at period end.
    Boolean cancelImmediately
) {
    public CancelSubscriptionRequest {
        if (cancelImmediately == null) cancelImmediately = false;
    }
}
