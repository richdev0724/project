package com.marketmono.dto.whop;

import jakarta.validation.constraints.Size;

public record WhopMembershipReviewNoteRequest(
    @Size(max = 500) String reviewNote
) {}
