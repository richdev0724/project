package com.marketmono.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Email verification request DTO.
 */
public record VerifyEmailRequest(
    @NotBlank(message = "Token is required")
    String token,
    String emailVerificationContinuationToken
) {}
