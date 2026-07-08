package com.marketmono.dto.waitlist;

import java.util.List;
import java.util.UUID;

public record BetaWaitlistInvitePreviewRequest(
    List<UUID> ids,
    BetaWaitlistSelectionMode selectionMode
) {
}
