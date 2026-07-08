package com.marketmono.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalEmailRequest(
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Recipient email must be valid")
    @Size(max = 320, message = "Recipient email must be 320 characters or less")
    String to,

    @NotBlank(message = "Email subject is required")
    @Size(max = 200, message = "Email subject must be 200 characters or less")
    String subject,

    @Size(max = 100_000, message = "HTML email content must be 100000 characters or less")
    String htmlContent,

    @Size(max = 100_000, message = "Text email content must be 100000 characters or less")
    String textContent
) {
}
