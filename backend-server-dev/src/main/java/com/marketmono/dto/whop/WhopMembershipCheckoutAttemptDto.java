package com.marketmono.dto.whop;

import java.time.LocalDateTime;
import java.util.UUID;

public record WhopMembershipCheckoutAttemptDto(
    UUID attemptId,
    UUID userId,
    String userEmail,
    String whopPlanId,
    String status,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    LocalDateTime completedAt
) {}
