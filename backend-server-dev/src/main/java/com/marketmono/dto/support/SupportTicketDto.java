package com.marketmono.dto.support;

import com.marketmono.entity.support.SupportTicketPriority;
import com.marketmono.entity.support.SupportTicketStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SupportTicketDto(
    UUID id,
    String ticketNumber,
    SupportTicketStatus status,
    SupportTicketPriority priority,
    String subject,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt,
    int unreadStaffMessageCount,
    LocalDateTime lastStaffReplyAt,
    List<SupportTicketMessageDto> messages
) {}
