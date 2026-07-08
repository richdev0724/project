package com.marketmono.service;

import com.marketmono.dto.ReferralSummaryDto;
import com.marketmono.entity.Referral;
import com.marketmono.entity.ReferralReward;
import com.marketmono.entity.ReferralRewardStatus;
import com.marketmono.entity.ReferralStatus;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.ReferralRepository;
import com.marketmono.repository.ReferralRewardRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.marketmono.entity.PaymentStatus.PAID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReferralRepository referralRepository;

    @Mock
    private ReferralRewardRepository referralRewardRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @InjectMocks
    private ReferralService referralService;

    @Test
    void shouldGenerateReferralCodeWhenUserDoesNotHaveOne() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("referrer@example.com")
            .emailLowercase("referrer@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();

        when(userRepository.existsByReferralCode(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = referralService.ensureReferralCode(user);

        assertThat(saved.getReferralCode()).isNotBlank();
        assertThat(saved.getReferralCode()).hasSize(8);
        assertThat(saved.getReferralCode()).matches("^[A-Z0-9]{8}$");
    }

    @Test
    void shouldAttachPendingReferralFromValidCode() {
        User referrer = User.builder()
            .id(UUID.randomUUID())
            .email("referrer@example.com")
            .emailLowercase("referrer@example.com")
            .referralCode("ABC12345")
            .enabled(true)
            .emailVerified(true)
            .build();
        User referred = User.builder()
            .id(UUID.randomUUID())
            .email("referred@example.com")
            .emailLowercase("referred@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();

        when(referralRepository.findByReferredUser(referred)).thenReturn(Optional.empty());
        when(userRepository.findByReferralCode("ABC12345")).thenReturn(Optional.of(referrer));
        ArgumentCaptor<Referral> referralCaptor = ArgumentCaptor.forClass(Referral.class);
        when(referralRepository.save(referralCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        referralService.attachReferral(referred, "abc-12345");

        Referral saved = referralCaptor.getValue();
        assertThat(saved.getReferrer()).isEqualTo(referrer);
        assertThat(saved.getReferredUser()).isEqualTo(referred);
        assertThat(saved.getReferralCode()).isEqualTo("ABC12345");
        assertThat(saved.getStatus()).isEqualTo(ReferralStatus.PENDING);
    }

    @Test
    void shouldRejectMalformedReferralCode() {
        User referred = User.builder()
            .id(UUID.randomUUID())
            .email("referred@example.com")
            .emailLowercase("referred@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();

        assertThatThrownBy(() -> referralService.attachReferral(referred, "bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Referral code is invalid");
    }

    @Test
    void shouldQualifyReferralAndCreatePendingRewardOnFirstPaidInvoice() {
        User referrer = User.builder()
            .id(UUID.randomUUID())
            .email("referrer@example.com")
            .emailLowercase("referrer@example.com")
            .referralCode("ABC12345")
            .enabled(true)
            .emailVerified(true)
            .build();
        User referred = User.builder()
            .id(UUID.randomUUID())
            .email("referred@example.com")
            .emailLowercase("referred@example.com")
            .enabled(true)
            .emailVerified(true)
            .createdAt(LocalDateTime.now().minusMonths(2))
            .build();
        Subscription qualifyingSubscription = Subscription.builder()
            .id(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .build();
        Referral referral = Referral.builder()
            .id(UUID.randomUUID())
            .referrer(referrer)
            .referredUser(referred)
            .referralCode("ABC12345")
            .status(ReferralStatus.PENDING)
            .build();

        when(referralRepository.findByReferredUser(referred)).thenReturn(Optional.of(referral));
        when(paymentHistoryRepository.countByUserIdAndStatus(referred.getId(), PAID)).thenReturn(1L);
        when(referralRepository.save(any(Referral.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(referralRewardRepository.existsByReferral(referral)).thenReturn(false);
        when(referralRewardRepository.save(any(ReferralReward.class))).thenAnswer(invocation -> invocation.getArgument(0));

        referralService.qualifyReferral(referred, qualifyingSubscription, "in_123");

        assertThat(referral.getStatus()).isEqualTo(ReferralStatus.QUALIFIED);
        assertThat(referral.getQualifyingSubscription()).isEqualTo(qualifyingSubscription);
        assertThat(referral.getQualifyingStripeInvoiceId()).isEqualTo("in_123");
        verify(referralRewardRepository).save(any(ReferralReward.class));
    }

    @Test
    void shouldBuildReferralSummaryFromLiveReferralData() {
        User referrer = User.builder()
            .id(UUID.randomUUID())
            .email("referrer@example.com")
            .emailLowercase("referrer@example.com")
            .referralCode("ABC12345")
            .enabled(true)
            .emailVerified(true)
            .build();
        User activeReferred = User.builder()
            .id(UUID.randomUUID())
            .email("alpha@example.com")
            .emailLowercase("alpha@example.com")
            .displayName("Alpha Trader")
            .enabled(true)
            .emailVerified(true)
            .createdAt(LocalDateTime.now().minusMonths(3))
            .build();
        User pendingReferred = User.builder()
            .id(UUID.randomUUID())
            .email("beta@example.com")
            .emailLowercase("beta@example.com")
            .enabled(true)
            .emailVerified(true)
            .createdAt(LocalDateTime.now().minusWeeks(2))
            .build();

        Referral activeReferral = Referral.builder()
            .id(UUID.randomUUID())
            .referrer(referrer)
            .referredUser(activeReferred)
            .referralCode("ABC12345")
            .status(ReferralStatus.QUALIFIED)
            .createdAt(LocalDateTime.now().minusMonths(3))
            .build();
        ReferralReward activeReward = ReferralReward.builder()
            .id(UUID.randomUUID())
            .referral(activeReferral)
            .user(referrer)
            .rewardAmount(new BigDecimal("5.00"))
            .currency("USD")
            .status(ReferralRewardStatus.APPLIED)
            .appliedAt(LocalDateTime.now().minusDays(2))
            .build();
        activeReferral.setReward(activeReward);
        Referral pendingReferral = Referral.builder()
            .id(UUID.randomUUID())
            .referrer(referrer)
            .referredUser(pendingReferred)
            .referralCode("ABC12345")
            .status(ReferralStatus.PENDING)
            .createdAt(LocalDateTime.now().minusWeeks(2))
            .build();

        when(referralRepository.findByReferrerOrderByCreatedAtDesc(referrer))
            .thenReturn(List.of(activeReferral, pendingReferral));
        when(subscriptionRepository.findActiveSubscriptionsByUserId(activeReferred.getId()))
            .thenReturn(List.of(Subscription.builder().id(UUID.randomUUID()).status(SubscriptionStatus.ACTIVE).build()));
        when(referralRewardRepository.countByUserIdAndStatus(referrer.getId(), ReferralRewardStatus.PENDING))
            .thenReturn(0L);
        when(referralRewardRepository.countByUserIdAndStatus(referrer.getId(), ReferralRewardStatus.APPROVED))
            .thenReturn(0L);
        when(referralRewardRepository.countByUserIdAndStatus(referrer.getId(), ReferralRewardStatus.APPLIED))
            .thenReturn(1L);
        when(referralRewardRepository.sumRewardAmountByUserIdAndStatus(referrer.getId(), ReferralRewardStatus.APPLIED))
            .thenReturn(new BigDecimal("5.00"));

        ReferralSummaryDto summary = referralService.getSummary(referrer);

        assertThat(summary.referralCode()).isEqualTo("ABC12345");
        assertThat(summary.totalReferrals()).isEqualTo(2);
        assertThat(summary.activeReferrals()).isEqualTo(1);
        assertThat(summary.qualifiedReferrals()).isEqualTo(1);
        assertThat(summary.pendingRewards()).isEqualTo(0);
        assertThat(summary.approvedRewards()).isEqualTo(0);
        assertThat(summary.appliedRewards()).isEqualTo(1);
        assertThat(summary.appliedRewardAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(summary.rewardPerActiveReferral()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(summary.estimatedMonthlyReward()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(summary.referrals()).hasSize(2);
        assertThat(summary.referrals().get(0).status()).isEqualTo("active");
        assertThat(summary.referrals().get(0).rewardStatus()).isEqualTo("applied");
        assertThat(summary.referrals().get(1).status()).isEqualTo("pending");
    }
}
