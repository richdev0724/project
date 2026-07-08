package com.marketmono.dto.waitlist;

import java.util.List;

public record BetaWaitlistInviteResponse(
    int requestedCount,
    int invitedCount,
    int skippedCount,
    List<WaitlistedUserDto> invitedUsers,
    List<BetaWaitlistInviteSkippedUserDto> skippedUsers
) {
}
