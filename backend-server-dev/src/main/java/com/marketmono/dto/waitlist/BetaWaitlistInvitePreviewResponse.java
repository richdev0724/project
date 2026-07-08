package com.marketmono.dto.waitlist;

import java.util.List;

public record BetaWaitlistInvitePreviewResponse(
    BetaWaitlistSelectionMode selectionMode,
    int eligibleCount,
    List<WaitlistedUserDto> sampleRecipients,
    int alreadyBetaCount,
    List<BetaWaitlistInviteSkippedUserDto> skippedUsers
) {
}
