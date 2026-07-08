package com.marketmono.dto.whop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WhopMembershipResolveWithoutActionRequest(
    @NotBlank @Size(max = 500) String reviewNote
) {}
