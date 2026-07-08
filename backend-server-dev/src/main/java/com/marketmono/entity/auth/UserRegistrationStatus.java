package com.marketmono.entity.auth;

/**
 * Lifecycle status for end-user registration and waitlist progression.
 */
public enum UserRegistrationStatus {
    WAITLISTED,
    PENDING_EMAIL_VERIFICATION,
    REGISTERED
}
