package com.marketmono.dto.auth;

/**
 * Authentication response with tokens and user info.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserDto user
) {}
