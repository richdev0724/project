package com.marketmono.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to verify 2FA code during login.
 */
public record TwoFactorVerifyRequest(
    @NotBlank(message = "2FA token is required")
    String twoFactorToken,
    
    @NotBlank(message = "Verification code is required")
    String code,
    
    /**
     * Whether the code is a recovery code (true) or TOTP code (false).
     */
    boolean isRecoveryCode
) {
    /**
     * Default constructor with isRecoveryCode = false.
     */
    public TwoFactorVerifyRequest(String twoFactorToken, String code) {
        this(twoFactorToken, code, false);
    }
}
