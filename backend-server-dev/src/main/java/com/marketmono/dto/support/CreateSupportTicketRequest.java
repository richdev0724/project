package com.marketmono.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
    @NotBlank
    @Size(max = 160)
    String subject,

    @NotBlank
    @Size(max = 5000)
    String message
) {}
