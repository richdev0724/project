package com.marketmono.dto.whop;

import java.time.LocalDateTime;

public record WhopMembershipCandidateEvidenceDto(
    String type,
    String source,
    LocalDateTime occurredAt,
    String description
) {}
