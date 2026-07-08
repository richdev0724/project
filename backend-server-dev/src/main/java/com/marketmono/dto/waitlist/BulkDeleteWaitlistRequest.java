package com.marketmono.dto.waitlist;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkDeleteWaitlistRequest(
    @NotEmpty(message = "ids is required")
    List<@NotNull UUID> ids
) {
}
