package com.marketmono.dto.auth;

import java.time.LocalDateTime;

/**
 * Registration response with optional same-browser email verification continuation metadata.
 */
public record RegisterResponse(
    String message,
    String emailVerificationContinuationToken,
    LocalDateTime emailVerificationContinuationExpiresAt
) {
    public static RegisterResponse success(String message, EmailVerificationContinuationToken continuationToken) {
        if (continuationToken == null) {
            return new RegisterResponse(message, null, null);
        }

        return new RegisterResponse(message, continuationToken.token(), continuationToken.expiresAt());
    }
}
