package com.marketmono.dto;

import jakarta.validation.constraints.Size;

/**
 * Internal admin request for revoking local subscription access.
 */
public record AdminRevokeSubscriptionRequest(
    @Size(max = 500, message = "Reason must be at most 500 characters")
    String reason
) {
}
