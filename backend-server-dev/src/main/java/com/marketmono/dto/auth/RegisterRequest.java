package com.marketmono.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration request DTO.
 */
public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @Size(max = 100, message = "Display name must be 100 characters or less")
    String displayName,

    @Pattern(
        regexp = "^[A-Za-z0-9]*$",
        message = "Referral code must contain only letters and numbers"
    )
    @Size(max = 8, message = "Referral code must be 8 characters or less")
    String referralCode,

    @Pattern(
        regexp = "^[A-Za-z0-9-]*$",
        message = "Promo code must contain only letters, numbers, and hyphens"
    )
    @Size(max = 40, message = "Promo code must be 40 characters or less")
    String promoCode,
    
    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    String password
) {
    public RegisterRequest(String email, String displayName, String referralCode, String password) {
        this(email, displayName, referralCode, null, password);
    }
}
