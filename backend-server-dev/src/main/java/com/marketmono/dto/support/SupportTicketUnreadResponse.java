package com.marketmono.dto.support;

import java.util.List;
import java.util.UUID;

public record SupportTicketUnreadResponse(
    int unreadCount,
    List<UUID> ticketIds
) {}
