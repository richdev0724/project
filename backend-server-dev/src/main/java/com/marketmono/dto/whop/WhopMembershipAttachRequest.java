package com.marketmono.dto.whop;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WhopMembershipAttachRequest(
    @NotNull UUID selectedUserId,
    @Size(max = 1000) String reason,
    boolean overrideConflicts
) {}
