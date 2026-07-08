package com.marketmono.dto.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminAppUserHardDeleteChallengeResponse(
    UUID challengeId,
    String challengeText,
    LocalDateTime notBefore,
    LocalDateTime expiresAt
) {
}
