package com.marketmono.dto.whop;

import com.marketmono.entity.WhopMembershipCorrelationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record WhopMembershipReviewItemDto(
    UUID correlationId,
    String buyerEmail,
    WhopMembershipCorrelationStatus status,
    String reviewReason,
    UUID productId,
    String productName,
    String whopMembershipId,
    String whopProductId,
    String whopPlanId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
