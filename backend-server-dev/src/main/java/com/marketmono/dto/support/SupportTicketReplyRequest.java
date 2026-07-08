package com.marketmono.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportTicketReplyRequest(
    @NotBlank
    @Size(max = 5000)
    String message
) {}
