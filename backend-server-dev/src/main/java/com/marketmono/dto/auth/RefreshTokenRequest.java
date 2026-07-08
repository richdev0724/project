package com.marketmono.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Token refresh request DTO.
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}
