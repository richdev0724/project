package com.marketmono.service.auth;

/**
 * Verified Google ID-token claims used by the auth service.
 */
public record GoogleIdTokenClaims(
    String subject,
    String email,
    boolean emailVerified,
    String name,
    String hostedDomain
) {
}
