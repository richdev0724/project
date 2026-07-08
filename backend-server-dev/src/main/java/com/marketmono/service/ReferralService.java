package com.marketmono.service;

import com.marketmono.dto.ReferralSummaryDto;
import com.marketmono.entity.Referral;
import com.marketmono.entity.ReferralReward;
import com.marketmono.entity.ReferralRewardStatus;
import com.marketmono.entity.ReferralStatus;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.ReferralRepository;
import com.marketmono.repository.ReferralRewardRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.marketmono.entity.PaymentStatus.PAID;

/**
 * Referral-program orchestration for code generation, signup capture, qualification, and dashboard summary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    private static final String REFERRAL_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERRAL_CODE_LENGTH = 8;
    private static final int MAX_CODE_ATTEMPTS = 50;
    private static final int PAYOUT_THRESHOLD = 8;
    private static final BigDecimal REWARD_PER_ACTIVE_REFERRAL = new BigDecimal("5.00");

    private final UserRepository userRepository;
    private final ReferralRepository referralRepository;
    private final ReferralRewardRepository referralRewardRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public User ensureReferralCode(User user) {
        if (user.getReferralCode() != null && !user.getReferralCode().isBlank()) {
            return user;
        }

        user.setReferralCode(generateUniqueCode());
        User saved = userRepository.save(user);
        log.debug("Assigned referral code {} to user {}", saved.getReferralCode(), saved.getEmail());
        return saved;
    }

    @Transactional
    public void attachReferral(User referredUser, String incomingReferralCode) {
        String referralCode = normalizeCode(incomingReferralCode);
        if (referralCode == null) {
            if (incomingReferralCode == null || incomingReferralCode.isBlank()) {
                return;
            }
            throw new IllegalArgumentException("Referral code is invalid");
        }

        if (referralRepository.findByReferredUser(referredUser).isPresent()) {
            throw new IllegalArgumentException("A referral has already been attached to this signup");
        }

        User referrer = userRepository.findByReferralCode(referralCode)
            .orElseThrow(() -> new IllegalArgumentException("Referral code is invalid"));

        if (!Boolean.TRUE.equals(referrer.getEnabled()) || !Boolean.TRUE.equals(referrer.getEmailVerified())) {
            throw new IllegalArgumentException("Referral code is not eligible for use");
        }

        if (referrer.getId().equals(referredUser.getId())) {
            throw new IllegalArgumentException("You cannot refer yourself");
        }

        referralRepository.save(Referral.builder()
            .referrer(referrer)
            .referredUser(referredUser)
            .referralCode(referralCode)
            .status(ReferralStatus.PENDING)
            .build());

        log.info("Attached referral code {} from {} to newly registered user {}",
            referralCode,
            referrer.getEmail(),
            referredUser.getEmail());
    }

    @Transactional
    public void qualifyReferral(User referredUser, Subscription qualifyingSubscription, String stripeInvoiceId) {
        Referral referral = referralRepository.findByReferredUser(referredUser)
            .orElse(null);
        if (referral == null) {
            return;
        }

        if (qualifyingSubscription == null || !qualifyingSubscription.hasAccess()) {
            log.debug("Skipping referral qualification for user {} because the subscription is not eligible",
                referredUser.getEmail());
            return;
        }

        if (!Boolean.TRUE.equals(referredUser.getEnabled()) || !Boolean.TRUE.equals(referredUser.getEmailVerified())) {
            log.debug("Skipping referral qualification for user {} because the account is not enabled/verified",
                referredUser.getEmail());
            return;
        }

        if (!Boolean.TRUE.equals(referral.getReferrer().getEnabled()) || !Boolean.TRUE.equals(referral.getReferrer().getEmailVerified())) {
            log.debug("Skipping referral qualification for referrer {} because the account is not enabled/verified",
                referral.getReferrer().getEmail());
            return;
        }

        if (referral.getStatus() == ReferralStatus.QUALIFIED) {
            return;
        }

        long paidInvoices = paymentHistoryRepository.countByUserIdAndStatus(referredUser.getId(), PAID);
        if (paidInvoices != 1) {
            log.debug("Skipping referral qualification for user {} because paid invoice count is {}",
                referredUser.getEmail(), paidInvoices);
            return;
        }

        referral.setStatus(ReferralStatus.QUALIFIED);
        referral.setQualifiedAt(LocalDateTime.now());
        referral.setQualifyingSubscription(qualifyingSubscription);
        referral.setQualifyingStripeInvoiceId(stripeInvoiceId);
        referralRepository.save(referral);

        if (!referralRewardRepository.existsByReferral(referral)) {
            referralRewardRepository.save(ReferralReward.builder()
                .referral(referral)
                .user(referral.getReferrer())
                .rewardAmount(REWARD_PER_ACTIVE_REFERRAL)
                .currency("USD")
                .status(ReferralRewardStatus.PENDING)
                .notes("Awaiting referral reward review")
                .build());
        }

        log.info("Qualified referral {} for referrer {} after invoice {}",
            referral.getId(),
            referral.getReferrer().getEmail(),
            stripeInvoiceId);
    }

    @Transactional
    public ReferralSummaryDto getSummary(User user) {
        User referrer = ensureReferralCode(user);
        List<Referral> referrals = referralRepository.findByReferrerOrderByCreatedAtDesc(referrer);
        List<ReferralSummaryDto.ReferralItemDto> items = new ArrayList<>();
        int activeReferrals = 0;
        int qualifiedReferrals = 0;

        for (Referral referral : referrals) {
            String status = deriveReferralActivityStatus(referral);
            if ("active".equals(status)) {
                activeReferrals++;
            }
            if (referral.getStatus() == ReferralStatus.QUALIFIED) {
                qualifiedReferrals++;
            }

            User referredUser = referral.getReferredUser();
            ReferralReward reward = referral.getReward();
            long monthsActive = calculateMonthsActive(referredUser.getCreatedAt());
            items.add(new ReferralSummaryDto.ReferralItemDto(
                referral.getId().toString(),
                buildReferralLabel(referredUser),
                referredUser.getCreatedAt(),
                status,
                monthsActive,
                reward != null ? reward.getStatus().name().toLowerCase(Locale.ROOT) : null,
                reward != null ? reward.getRewardAmount() : null,
                reward != null ? reward.getAppliedAt() : null
            ));
        }

        int totalReferrals = referrals.size();
        int pendingRewards = (int) referralRewardRepository.countByUserIdAndStatus(
            referrer.getId(),
            ReferralRewardStatus.PENDING
        );
        int approvedRewards = (int) referralRewardRepository.countByUserIdAndStatus(
            referrer.getId(),
            ReferralRewardStatus.APPROVED
        );
        int appliedRewards = (int) referralRewardRepository.countByUserIdAndStatus(
            referrer.getId(),
            ReferralRewardStatus.APPLIED
        );
        BigDecimal appliedRewardAmount = referralRewardRepository.sumRewardAmountByUserIdAndStatus(
            referrer.getId(),
            ReferralRewardStatus.APPLIED
        );
        boolean payoutUnlocked = totalReferrals >= PAYOUT_THRESHOLD;
        BigDecimal estimatedMonthlyReward = REWARD_PER_ACTIVE_REFERRAL.multiply(BigDecimal.valueOf(activeReferrals));

        return new ReferralSummaryDto(
            referrer.getReferralCode(),
            totalReferrals,
            activeReferrals,
            qualifiedReferrals,
            pendingRewards,
            approvedRewards,
            appliedRewards,
            appliedRewardAmount,
            REWARD_PER_ACTIVE_REFERRAL,
            estimatedMonthlyReward,
            PAYOUT_THRESHOLD,
            payoutUnlocked,
            items
        );
    }

    private String deriveReferralActivityStatus(Referral referral) {
        if (referral.getStatus() == ReferralStatus.PENDING) {
            return "pending";
        }
        if (referral.getStatus() == ReferralStatus.REJECTED) {
            return "rejected";
        }
        return subscriptionRepository.findActiveSubscriptionsByUserId(referral.getReferredUser().getId()).isEmpty()
            ? "churned"
            : "active";
    }

    private long calculateMonthsActive(LocalDateTime joinedAt) {
        if (joinedAt == null) {
            return 0;
        }
        long months = ChronoUnit.MONTHS.between(joinedAt, LocalDateTime.now());
        return Math.max(months, 0);
    }

    private String buildReferralLabel(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }

        String email = user.getEmail();
        if (email == null || !email.contains("@")) {
            return "User";
        }

        String[] parts = email.split("@", 2);
        String localPart = parts[0];
        String domain = parts[1];
        String prefix = localPart.substring(0, Math.min(localPart.length(), 3));
        return prefix + "***@" + domain;
    }

    private String normalizeCode(String referralCode) {
        if (referralCode == null) {
            return null;
        }

        String normalized = referralCode.trim()
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() == REFERRAL_CODE_LENGTH ? normalized : null;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!userRepository.existsByReferralCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique referral code");
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(REFERRAL_CODE_LENGTH);
        for (int i = 0; i < REFERRAL_CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(REFERRAL_ALPHABET.length());
            builder.append(REFERRAL_ALPHABET.charAt(index));
        }
        return builder.toString();
    }
}
