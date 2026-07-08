package com.marketmono.dto.whop;

import java.time.LocalDateTime;
import java.util.UUID;

public record WhopMembershipWebhookLedgerDto(
    UUID id,
    String externalEventId,
    String eventType,
    String processingStatus,
    String errorMessage,
    LocalDateTime receivedAt,
    LocalDateTime processedAt
) {}
