package com.marketmono.dto.waitlist;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public waitlist submission request.
 */
public record WaitlistRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be 100 characters or less")
    String name,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must be 255 characters or less")
    String email
) {}
