package com.marketmono.dto.waitlist;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BetaWaitlistInviteRequest(
    List<UUID> ids,
    BetaWaitlistSelectionMode selectionMode,
    @Size(max = 180, message = "Template subject must be at most 180 characters")
    String templateSubject,
    @Size(max = 20000, message = "Template HTML must be at most 20000 characters")
    String templateHtml,
    @Size(max = 10000, message = "Template text must be at most 10000 characters")
    String templateText,
    Boolean saveTemplate,
    Integer ttlHours
) {
}
