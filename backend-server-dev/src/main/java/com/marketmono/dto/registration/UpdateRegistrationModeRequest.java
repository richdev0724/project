package com.marketmono.dto.registration;

import com.marketmono.entity.auth.RegistrationMode;
import jakarta.validation.constraints.NotNull;

/**
 * Admin request to change public registration availability.
 */
public record UpdateRegistrationModeRequest(
    @NotNull(message = "Registration mode is required")
    RegistrationMode mode
) {}
