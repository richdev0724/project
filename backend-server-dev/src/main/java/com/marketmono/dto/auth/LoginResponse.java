package com.marketmono.dto.auth;

/**
 * Login response that can indicate if 2FA is required.
 */
public record LoginResponse(
    boolean requiresTwoFactor,
    String twoFactorToken,  // Temporary token for 2FA verification
    String accessToken,
    String refreshToken,
    UserDto user
) {
    /**
     * Create a response indicating 2FA is required.
     */
    public static LoginResponse twoFactorRequired(String twoFactorToken) {
        return new LoginResponse(true, twoFactorToken, null, null, null);
    }
    
    /**
     * Create a successful login response with tokens.
     */
    public static LoginResponse success(String accessToken, String refreshToken, UserDto user) {
        return new LoginResponse(false, null, accessToken, refreshToken, user);
    }
}
