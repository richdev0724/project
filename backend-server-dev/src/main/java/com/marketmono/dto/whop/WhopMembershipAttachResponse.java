package com.marketmono.dto.whop;

import com.marketmono.entity.WhopMembershipCorrelationStatus;

import java.util.UUID;

public record WhopMembershipAttachResponse(
    UUID correlationId,
    UUID selectedUserId,
    UUID subscriptionId,
    WhopMembershipCorrelationStatus status,
    int expiredCheckoutAttempts
) {}
