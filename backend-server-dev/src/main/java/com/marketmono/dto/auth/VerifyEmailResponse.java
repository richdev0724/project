package com.marketmono.dto.auth;

/**
 * Email verification response. Final auth tokens are present only when same-browser auto-login succeeds.
 */
public record VerifyEmailResponse(
    String message,
    boolean autoLoggedIn,
    boolean requiresTwoFactor,
    String twoFactorToken,
    String accessToken,
    String refreshToken,
    UserDto user
) {
    public static VerifyEmailResponse verifiedOnly(String message) {
        return new VerifyEmailResponse(message, false, false, null, null, null, null);
    }

    public static VerifyEmailResponse twoFactorRequired(String message, String twoFactorToken) {
        return new VerifyEmailResponse(message, false, true, twoFactorToken, null, null, null);
    }

    public static VerifyEmailResponse autoLoggedIn(String message, LoginResponse loginResponse) {
        return new VerifyEmailResponse(
            message,
            true,
            false,
            null,
            loginResponse.accessToken(),
            loginResponse.refreshToken(),
            loginResponse.user()
        );
    }
}
