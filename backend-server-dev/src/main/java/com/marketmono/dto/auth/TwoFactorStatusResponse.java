package com.marketmono.dto.auth;

/**
 * Response DTO for 2FA status endpoint.
 */
public record TwoFactorStatusResponse(
    boolean enabled,
    int remainingRecoveryCodes
) {}
