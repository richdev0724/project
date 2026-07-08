package com.marketmono.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Authenticated referral dashboard payload for the settings screen.
 */
public record ReferralSummaryDto(
    String referralCode,
    int totalReferrals,
    int activeReferrals,
    int qualifiedReferrals,
    int pendingRewards,
    int approvedRewards,
    int appliedRewards,
    BigDecimal appliedRewardAmount,
    BigDecimal rewardPerActiveReferral,
    BigDecimal estimatedMonthlyReward,
    int payoutThreshold,
    boolean payoutUnlocked,
    List<ReferralItemDto> referrals
) {
    public record ReferralItemDto(
        String id,
        String label,
        LocalDateTime joinedAt,
        String status,
        long monthsActive,
        String rewardStatus,
        BigDecimal rewardAmount,
        LocalDateTime rewardAppliedAt
    ) {}
}
