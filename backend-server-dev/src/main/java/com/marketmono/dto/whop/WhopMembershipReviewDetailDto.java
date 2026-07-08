package com.marketmono.dto.whop;

import com.marketmono.entity.WhopMembershipCorrelationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WhopMembershipReviewDetailDto(
    UUID correlationId,
    String whopMembershipId,
    String buyerEmail,
    WhopMembershipCorrelationStatus status,
    String reviewReason,
    UUID productId,
    String productName,
    String whopProductId,
    String whopPlanId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    WhopMembershipWebhookLedgerDto webhookLedger,
    List<WhopMembershipCheckoutAttemptDto> checkoutAttempts,
    List<WhopMembershipCandidateUserDto> candidates
) {}
