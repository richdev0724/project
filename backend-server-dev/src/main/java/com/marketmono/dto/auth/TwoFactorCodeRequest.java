package com.marketmono.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for 2FA TOTP code verification during setup/regenerate.
 * Only contains the code field (no token needed - user is already authenticated).
 */
public record TwoFactorCodeRequest(
    @NotBlank(message = "Verification code is required")
    @Size(min = 6, max = 8, message = "Code must be 6-8 characters")
    String code
) {}
