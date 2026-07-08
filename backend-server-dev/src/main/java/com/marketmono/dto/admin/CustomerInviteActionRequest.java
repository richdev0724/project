package com.marketmono.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CustomerInviteActionRequest(
    @Min(value = 1, message = "TTL must be at least 1 hour")
    @Max(value = 168, message = "TTL must be 168 hours or less")
    Integer ttlHours
) {
}
