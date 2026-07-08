package com.marketmono.dto.auth;

import java.util.List;

/**
 * Response DTO for 2FA enable/regenerate operations.
 * Contains recovery codes and success status.
 */
public record TwoFactorEnableResponse(
    boolean success,
    List<String> recoveryCodes,
    String message
) {}
