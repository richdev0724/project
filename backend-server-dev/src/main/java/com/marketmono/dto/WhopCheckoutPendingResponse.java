package com.marketmono.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Describes whether the user has an in-flight Whop checkout or is waiting
 * for webhook provisioning after payment.
 */
public record WhopCheckoutPendingResponse(
    boolean hasPendingCheckout,
    String state,
    UUID attemptId,
    String buyerEmail,
    String purchaseUrl,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    LocalDateTime completedAt
) {
    public static final String STATE_NONE = "NONE";
    public static final String STATE_CREATED = "CREATED";
    public static final String STATE_ACTIVATING = "ACTIVATING";

    public static WhopCheckoutPendingResponse none() {
        return new WhopCheckoutPendingResponse(
            false,
            STATE_NONE,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public static WhopCheckoutPendingResponse created(
            UUID attemptId,
            String buyerEmail,
            String purchaseUrl,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        return new WhopCheckoutPendingResponse(
            true,
            STATE_CREATED,
            attemptId,
            buyerEmail,
            purchaseUrl,
            createdAt,
            expiresAt,
            null
        );
    }

    public static WhopCheckoutPendingResponse activating(
            UUID attemptId,
            String buyerEmail,
            LocalDateTime createdAt,
            LocalDateTime completedAt) {
        return new WhopCheckoutPendingResponse(
            true,
            STATE_ACTIVATING,
            attemptId,
            buyerEmail,
            null,
            createdAt,
            null,
            completedAt
        );
    }
}
