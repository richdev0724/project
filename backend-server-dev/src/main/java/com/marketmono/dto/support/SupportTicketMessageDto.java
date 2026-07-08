package com.marketmono.dto.support;

import com.marketmono.entity.support.SupportTicketMessageAuthorType;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupportTicketMessageDto(
    UUID id,
    SupportTicketMessageAuthorType authorType,
    String authorDisplayName,
    String body,
    LocalDateTime createdAt,
    LocalDateTime readByUserAt
) {}
