package com.marketmono.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request from Google Identity Services containing the Google ID token.
 */
public record GoogleLoginRequest(
    @NotBlank(message = "Google credential is required")
    String credential
) {
}
