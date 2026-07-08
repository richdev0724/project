package com.marketmono.dto.whop;

import java.util.List;
import java.util.UUID;

public record WhopMembershipCandidateUserDto(
    UUID userId,
    String email,
    String displayName,
    WhopMembershipCandidateConfidence confidence,
    List<WhopMembershipCandidateEvidenceDto> evidence,
    boolean blocked,
    List<String> blockingReasons
) {}
