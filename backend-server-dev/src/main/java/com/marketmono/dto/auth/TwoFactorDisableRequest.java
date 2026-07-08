package com.marketmono.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for disabling 2FA.
 * Requires password verification for security.
 */
public record TwoFactorDisableRequest(
    @NotBlank(message = "Password is required")
    String password
) {}
