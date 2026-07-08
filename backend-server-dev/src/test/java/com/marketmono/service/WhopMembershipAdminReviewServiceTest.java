package com.marketmono.service;

import com.marketmono.dto.whop.WhopMembershipAttachRequest;
import com.marketmono.dto.whop.WhopMembershipAttachResponse;
import com.marketmono.dto.whop.WhopMembershipCandidateConfidence;
import com.marketmono.dto.whop.WhopMembershipReviewDetailDto;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.BillingWebhookEvent;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.WhopCheckoutAttempt;
import com.marketmono.entity.WhopCheckoutAttemptStatus;
import com.marketmono.entity.WhopMembershipCorrelation;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.BillingWebhookEventRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.WhopCheckoutAttemptRepository;
import com.marketmono.repository.WhopMembershipCorrelationRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WhopMembershipAdminReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-06-28T12:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    private WhopMembershipCorrelationRepository whopMembershipCorrelationRepository;

    @Mock
    private WhopCheckoutAttemptRepository whopCheckoutAttemptRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingWebhookEventRepository billingWebhookEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Test
    void shouldNormalizeBlankReviewSearchBeforeQueryingRepository() {
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        WhopMembershipAdminReviewService service = service();
        Pageable pageable = PageRequest.of(0, 25);

        when(whopMembershipCorrelationRepository.findReviews(any(), eq(""), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(correlation), pageable, 1));

        service.listReviews(WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT, null, false, pageable);

        verify(whopMembershipCorrelationRepository).findReviews(
            List.of(WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT),
            "",
            pageable
        );
    }

    @Test
    void shouldIncludeResolvedReviewStatusesWhenRequestedWithoutSpecificStatus() {
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        WhopMembershipAdminReviewService service = service();
        Pageable pageable = PageRequest.of(0, 25);

        when(whopMembershipCorrelationRepository.findReviews(any(), eq(""), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(correlation), pageable, 1));

        service.listReviews(null, null, true, pageable);

        verify(whopMembershipCorrelationRepository).findReviews(
            List.of(WhopMembershipCorrelationStatus.values()),
            "",
            pageable
        );
    }

    @Test
    void shouldRecommendExactEmailCandidateWithCheckoutAttemptEvidence() {
        User user = user("buyer@example.com");
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        WhopCheckoutAttempt attempt = checkoutAttempt(user, product.getPrices().getFirst());
        BillingWebhookEvent webhookEvent = BillingWebhookEvent.builder()
            .id(UUID.randomUUID())
            .billingProvider(BillingProvider.WHOP)
            .externalEventId("msg_orphan")
            .eventType("membership_activated")
            .processingStatus(BillingWebhookEvent.STATUS_NEEDS_REVIEW)
            .errorMessage("Needs review")
            .receivedAt(LocalDateTime.now(CLOCK).minusMinutes(20))
            .build();
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findById(correlation.getId()))
            .thenReturn(Optional.of(correlation));
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(
            BillingProvider.WHOP,
            "msg_orphan"
        )).thenReturn(Optional.of(webhookEvent));
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(whopCheckoutAttemptRepository.findTop10ByLockedBuyerEmailIgnoreCaseOrderByCreatedAtDesc(
            "buyer@example.com"
        )).thenReturn(List.of(attempt));
        when(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());

        WhopMembershipReviewDetailDto detail = service.getReviewDetail(correlation.getId());

        assertThat(detail.correlationId()).isEqualTo(correlation.getId());
        assertThat(detail.webhookLedger()).isNotNull();
        assertThat(detail.webhookLedger().processingStatus())
            .isEqualTo(BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertThat(detail.checkoutAttempts()).hasSize(1);
        assertThat(detail.checkoutAttempts().getFirst().attemptId()).isEqualTo(attempt.getId());
        assertThat(detail.candidates()).hasSize(1);
        assertThat(detail.candidates().getFirst().userId()).isEqualTo(user.getId());
        assertThat(detail.candidates().getFirst().confidence())
            .isEqualTo(WhopMembershipCandidateConfidence.HIGH);
        assertThat(detail.candidates().getFirst().evidence())
            .extracting(evidence -> evidence.type())
            .containsExactly("EMAIL_MATCH", "CHECKOUT_ATTEMPT_MATCH");
    }

    @Test
    void shouldAttachPublicNoAttemptMembershipWithProviderSubscriptionAuditAndWebhookUpdate() {
        UUID adminId = UUID.randomUUID();
        User user = user("buyer@example.com");
        Product product = publicProduct();
        ProductPrice price = product.getPrices().getFirst();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        BillingWebhookEvent webhookEvent = BillingWebhookEvent.builder()
            .id(UUID.randomUUID())
            .billingProvider(BillingProvider.WHOP)
            .externalEventId("msg_orphan")
            .eventType("membership_activated")
            .processingStatus(BillingWebhookEvent.STATUS_NEEDS_REVIEW)
            .errorMessage("Public Whop membership did not originate from a MarketMono checkout attempt")
            .build();
        WhopCheckoutAttempt expiredAttempt = checkoutAttempt(user, price);
        expiredAttempt.setStatus(WhopCheckoutAttemptStatus.CREATED);
        expiredAttempt.setExpiresAt(LocalDateTime.now(CLOCK).minusMinutes(10));
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findByIdForUpdate(correlation.getId()))
            .thenReturn(Optional.of(correlation));
        when(userRepository.findActiveByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(
            BillingProvider.WHOP,
            "mem_orphan"
        )).thenReturn(Optional.empty());
        when(subscriptionRepository.hasWebsiteAccessSubscription(user.getId())).thenReturn(false);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            subscription.setId(UUID.randomUUID());
            return subscription;
        });
        when(whopMembershipCorrelationRepository.save(any(WhopMembershipCorrelation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(
            BillingProvider.WHOP,
            "msg_orphan"
        )).thenReturn(Optional.of(webhookEvent));
        when(whopCheckoutAttemptRepository.findExpiredCreatedAttemptsForReview(
            "buyer@example.com",
            "plan_monthly",
            LocalDateTime.now(CLOCK)
        )).thenReturn(List.of(expiredAttempt));

        WhopMembershipAttachResponse response = service.attachMembership(
            correlation.getId(),
            new WhopMembershipAttachRequest(user.getId(), "Whop payment verified by support", false),
            adminId,
            "127.0.0.1",
            "JUnit"
        );

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription subscription = subscriptionCaptor.getValue();
        assertThat(subscription.getBillingProvider()).isEqualTo(BillingProvider.WHOP);
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("mem_orphan");
        assertThat(subscription.getProviderUserId()).isEqualTo("whop_user");
        assertThat(subscription.getProviderProductId()).isEqualTo("prod_public");
        assertThat(subscription.getProviderPlanId()).isEqualTo("plan_monthly");
        assertThat(subscription.getProviderStatus()).isEqualTo("active");
        assertThat(subscription.getStripeMode()).isEqualTo(StripeMode.DEFAULT);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(subscription.getPriceAtSubscription()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(subscription.getCurrentPeriodStart()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(LocalDateTime.now(CLOCK).plusMonths(1));
        assertThat(correlation.getStatus()).isEqualTo(WhopMembershipCorrelationStatus.MATCHED);
        assertThat(correlation.getUser()).isEqualTo(user);
        assertThat(correlation.getSubscription()).isEqualTo(subscription);
        assertThat(correlation.getReviewReason()).isNull();
        assertThat(webhookEvent.getProcessingStatus()).isEqualTo(BillingWebhookEvent.STATUS_PROCESSED);
        assertThat(expiredAttempt.getStatus()).isEqualTo(WhopCheckoutAttemptStatus.EXPIRED);
        assertThat(response.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(response.expiredCheckoutAttempts()).isEqualTo(1);

        verify(adminAuditLogService).recordEvent(
            eq(adminId),
            eq("WHOP_MEMBERSHIP_ATTACHED"),
            eq("WHOP_MEMBERSHIP_CORRELATION"),
            eq(correlation.getId().toString()),
            any(Map.class)
        );
    }

    @Test
    void shouldResolveWithoutActionWithMandatoryAuditAndWebhookUpdate() {
        UUID adminId = UUID.randomUUID();
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        BillingWebhookEvent webhookEvent = BillingWebhookEvent.builder()
            .id(UUID.randomUUID())
            .billingProvider(BillingProvider.WHOP)
            .externalEventId("msg_orphan")
            .eventType("membership_activated")
            .processingStatus(BillingWebhookEvent.STATUS_NEEDS_REVIEW)
            .errorMessage("Public Whop membership did not originate from a MarketMono checkout attempt")
            .build();
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findByIdForUpdate(correlation.getId()))
            .thenReturn(Optional.of(correlation));
        when(whopMembershipCorrelationRepository.save(correlation)).thenReturn(correlation);
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(
            BillingProvider.WHOP,
            "msg_orphan"
        )).thenReturn(Optional.of(webhookEvent));
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.findTop10ByLockedBuyerEmailIgnoreCaseOrderByCreatedAtDesc(
            "buyer@example.com"
        )).thenReturn(List.of());

        WhopMembershipReviewDetailDto detail = service.resolveWithoutAction(
            correlation.getId(),
            "Duplicate testing case was already fixed outside CRM",
            adminId,
            "127.0.0.1",
            "JUnit"
        );

        assertThat(correlation.getStatus()).isEqualTo(WhopMembershipCorrelationStatus.RESOLVED_NO_ACTION);
        assertThat(correlation.getReviewReason())
            .isEqualTo("Duplicate testing case was already fixed outside CRM");
        assertThat(webhookEvent.getProcessingStatus()).isEqualTo(BillingWebhookEvent.STATUS_PROCESSED);
        assertThat(detail.status()).isEqualTo(WhopMembershipCorrelationStatus.RESOLVED_NO_ACTION);
        assertThat(detail.reviewReason()).isEqualTo("Duplicate testing case was already fixed outside CRM");
        verify(subscriptionRepository, never()).save(any(Subscription.class));

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(adminAuditLogService).recordEvent(
            eq(adminId),
            eq("WHOP_MEMBERSHIP_RESOLVED_WITHOUT_ACTION"),
            eq("WHOP_MEMBERSHIP_CORRELATION"),
            eq(correlation.getId().toString()),
            detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue())
            .containsEntry("membershipId", "mem_orphan")
            .containsEntry("buyerEmail", "buyer@example.com")
            .containsEntry("reason", "Duplicate testing case was already fixed outside CRM")
            .containsEntry("ipAddress", "127.0.0.1")
            .containsEntry("userAgent", "JUnit");
    }

    @Test
    void shouldRejectResolveWithoutActionWhenReviewNoteIsBlank() {
        UUID adminId = UUID.randomUUID();
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findByIdForUpdate(correlation.getId()))
            .thenReturn(Optional.of(correlation));

        assertThatThrownBy(() -> service.resolveWithoutAction(
            correlation.getId(),
            "   ",
            adminId,
            "127.0.0.1",
            "JUnit"
        ))
            .isInstanceOf(WhopMembershipAdminReviewService.WhopMembershipReviewConflictException.class)
            .hasMessageContaining("Review note is required");

        verify(whopMembershipCorrelationRepository, never()).save(any(WhopMembershipCorrelation.class));
        verify(adminAuditLogService, never()).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    void shouldBlockAttachWhenSelectedUserAlreadyHasWebsiteAccess() {
        UUID adminId = UUID.randomUUID();
        User user = user("buyer@example.com");
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findByIdForUpdate(correlation.getId()))
            .thenReturn(Optional.of(correlation));
        when(userRepository.findActiveByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(subscriptionRepository.hasWebsiteAccessSubscription(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.attachMembership(
            correlation.getId(),
            new WhopMembershipAttachRequest(user.getId(), "Whop payment verified by support", false),
            adminId,
            "127.0.0.1",
            "JUnit"
        ))
            .isInstanceOf(WhopMembershipAdminReviewService.WhopMembershipReviewConflictException.class)
            .hasMessageContaining("already has an access-granting subscription");

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        verify(adminAuditLogService, never()).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    void shouldBlockEmailMismatchUnlessOverrideRequested() {
        UUID adminId = UUID.randomUUID();
        User user = user("different@example.com");
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findByIdForUpdate(correlation.getId()))
            .thenReturn(Optional.of(correlation));
        when(userRepository.findActiveByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(subscriptionRepository.hasWebsiteAccessSubscription(user.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.attachMembership(
            correlation.getId(),
            new WhopMembershipAttachRequest(user.getId(), "Whop payment verified by support", false),
            adminId,
            "127.0.0.1",
            "JUnit"
        ))
            .isInstanceOf(WhopMembershipAdminReviewService.WhopMembershipReviewConflictException.class)
            .hasMessageContaining("email differs");

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void shouldReturnExistingSubscriptionForIdempotentMatchedSameUserAttach() {
        UUID adminId = UUID.randomUUID();
        User user = user("buyer@example.com");
        Product product = publicProduct();
        Subscription existingSubscription = new Subscription();
        existingSubscription.setId(UUID.randomUUID());
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        correlation.setStatus(WhopMembershipCorrelationStatus.MATCHED);
        correlation.setUser(user);
        correlation.setSubscription(existingSubscription);
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findByIdForUpdate(correlation.getId()))
            .thenReturn(Optional.of(correlation));

        WhopMembershipAttachResponse response = service.attachMembership(
            correlation.getId(),
            new WhopMembershipAttachRequest(user.getId(), "Retry after admin timeout", false),
            adminId,
            "127.0.0.1",
            "JUnit"
        );

        assertThat(response.subscriptionId()).isEqualTo(existingSubscription.getId());
        assertThat(response.status()).isEqualTo(WhopMembershipCorrelationStatus.MATCHED);
        assertThat(response.expiredCheckoutAttempts()).isZero();
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void shouldUpdateReviewNoteWithoutAttachingMembership() {
        User user = user("buyer@example.com");
        Product product = publicProduct();
        WhopMembershipCorrelation correlation = publicNoAttemptCorrelation(product);
        WhopMembershipAdminReviewService service = service();

        when(whopMembershipCorrelationRepository.findByIdForUpdate(correlation.getId()))
            .thenReturn(Optional.of(correlation));
        when(whopMembershipCorrelationRepository.save(correlation)).thenReturn(correlation);
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(
            BillingProvider.WHOP,
            "msg_orphan"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(whopCheckoutAttemptRepository.findTop10ByLockedBuyerEmailIgnoreCaseOrderByCreatedAtDesc(
            "buyer@example.com"
        )).thenReturn(List.of());
        when(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of());

        WhopMembershipReviewDetailDto detail = service.updateReviewNote(
            correlation.getId(),
            "Admin is waiting on customer confirmation"
        );

        assertThat(correlation.getReviewReason()).isEqualTo("Admin is waiting on customer confirmation");
        assertThat(detail.reviewReason()).isEqualTo("Admin is waiting on customer confirmation");
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    private WhopMembershipAdminReviewService service() {
        return new WhopMembershipAdminReviewService(
            whopMembershipCorrelationRepository,
            whopCheckoutAttemptRepository,
            subscriptionRepository,
            billingWebhookEventRepository,
            userRepository,
            adminAuditLogService,
            CLOCK
        );
    }

    private static User user(String email) {
        User user = User.builder()
            .id(UUID.randomUUID())
            .enabled(true)
            .emailVerified(true)
            .build();
        user.setEmail(email);
        return user;
    }

    private static Product publicProduct() {
        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono Monthly")
            .slug("market-mono-monthly")
            .whopProductId("prod_public")
            .isActive(true)
            .isArchived(false)
            .visibility(ProductVisibility.PUBLIC)
            .build();
        product.addPrice(ProductPrice.builder()
            .id(UUID.randomUUID())
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_monthly")
            .isActive(true)
            .build());
        return product;
    }

    private static WhopMembershipCorrelation publicNoAttemptCorrelation(Product product) {
        return WhopMembershipCorrelation.builder()
            .id(UUID.randomUUID())
            .whopMembershipId("mem_orphan")
            .whopUserId("whop_user")
            .buyerEmail("buyer@example.com")
            .whopProductId("prod_public")
            .whopPlanId("plan_monthly")
            .lastWebhookEventId("msg_orphan")
            .status(WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT)
            .reviewReason("Public Whop membership did not originate from a MarketMono checkout attempt")
            .product(product)
            .build();
    }

    private static WhopCheckoutAttempt checkoutAttempt(User user, ProductPrice price) {
        return WhopCheckoutAttempt.builder()
            .id(UUID.randomUUID())
            .user(user)
            .productPrice(price)
            .whopPlanId("plan_monthly")
            .status(WhopCheckoutAttemptStatus.CREATED)
            .lockedBuyerEmail("buyer@example.com")
            .buyerIdentityLocked(true)
            .buyerIdentityLockMethod("metadata")
            .createdAt(LocalDateTime.now(CLOCK).minusMinutes(30))
            .expiresAt(LocalDateTime.now(CLOCK).minusMinutes(1))
            .build();
    }
}
