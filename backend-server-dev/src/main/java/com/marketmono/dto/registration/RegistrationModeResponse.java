package com.marketmono.dto.registration;

import com.marketmono.entity.auth.RegistrationMode;

import java.time.LocalDateTime;

/**
 * Public-safe registration availability response.
 */
public record RegistrationModeResponse(
    RegistrationMode mode,
    boolean registrationOpen,
    boolean waitlistOpen,
    LocalDateTime updatedAt
) {
    public static RegistrationModeResponse from(RegistrationMode mode, LocalDateTime updatedAt) {
        return new RegistrationModeResponse(
            mode,
            mode == RegistrationMode.OPEN,
            mode == RegistrationMode.WAITLIST,
            updatedAt
        );
    }
}
