package com.marketmono.dto.auth;

import java.time.LocalDateTime;

/**
 * Same-browser token that can continue a just-created registration after email verification.
 */
public record EmailVerificationContinuationToken(
    String token,
    LocalDateTime expiresAt
) {}
