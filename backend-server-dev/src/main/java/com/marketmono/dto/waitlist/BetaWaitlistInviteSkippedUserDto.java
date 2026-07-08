package com.marketmono.dto.waitlist;

import java.util.UUID;

public record BetaWaitlistInviteSkippedUserDto(
    UUID id,
    String email,
    String reason
) {
}
