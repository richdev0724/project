package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.BillingWebhookEvent;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.WhopCheckoutAttempt;
import com.marketmono.entity.WhopCheckoutAttemptStatus;
import com.marketmono.entity.WhopMembershipCorrelation;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.repository.BillingWebhookEventRepository;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.WhopCheckoutAttemptRepository;
import com.marketmono.repository.WhopMembershipCorrelationRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhopWebhookServiceTest {

    private static final String WEBHOOK_TIMESTAMP = "1800000000";
    private static final String WEBHOOK_SIGNATURE = "v1,signature";

    @Mock
    private WhopWebhookVerifier verifier;

    @Mock
    private BillingWebhookEventRepository billingWebhookEventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WhopCheckoutAttemptRepository whopCheckoutAttemptRepository;

    @Mock
    private WhopMembershipCorrelationRepository whopMembershipCorrelationRepository;

    @Mock
    private SubscriberCapService subscriberCapService;

    private WhopWebhookService whopWebhookService;

    @BeforeEach
    void setUp() {
        whopWebhookService = new WhopWebhookService(
            verifier,
            new ObjectMapper(),
            billingWebhookEventRepository,
            subscriptionRepository,
            productRepository,
            paymentHistoryRepository,
            userRepository,
            whopCheckoutAttemptRepository,
            whopMembershipCorrelationRepository,
            subscriberCapService,
            new BetaWhopCheckoutService(productRepository, userRepository)
        );
        lenient().when(whopMembershipCorrelationRepository.findByWhopMembershipId(any()))
            .thenReturn(Optional.empty());
        lenient().when(whopMembershipCorrelationRepository.save(any(WhopMembershipCorrelation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreateActiveWhopSubscriptionWhenMembershipActivated() {
        allowLedgerSaves();
        User user = user("buyer@example.com");
        Product product = product();
        ProductPrice price = ProductPrice.builder()
            .id(UUID.randomUUID())
            .product(product)
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_monthly")
            .isActive(true)
            .build();
        product.addPrice(price);
        UUID attemptId = UUID.randomUUID();
        WhopCheckoutAttempt attempt = WhopCheckoutAttempt.builder()
            .id(attemptId)
            .user(user)
            .productPrice(price)
            .whopPlanId("plan_monthly")
            .status(WhopCheckoutAttemptStatus.CREATED)
            .buyerIdentityLocked(true)
            .lockedBuyerEmail("buyer@example.com")
            .buyerIdentityLockMethod("HOSTED_URL_EMAIL_HIDDEN")
            .build();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_member_active"))
            .thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.findByIdAndStatus(attemptId, WhopCheckoutAttemptStatus.CREATED))
            .thenReturn(Optional.of(attempt));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_123"))
            .thenReturn(Optional.empty());

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        whopWebhookService.handleWebhook(
            "msg_member_active",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership.activated",
                  "data": {
                    "id": "mem_123",
                    "status": "active",
                    "user": {"id": "whop_user_123", "email": "buyer@example.com"},
                    "member": {"id": "member_123"},
                    "product": {"id": "prod_monthly"},
                    "plan": {"id": "plan_monthly"},
                    "manage_url": "https://whop.com/hub/memberships/mem_123",
                    "renewal_period_start": "2027-01-15T08:00:00Z",
                    "renewal_period_end": "2027-02-14T08:00:00Z",
                    "cancel_at_period_end": false,
                    "metadata": {
                      "marketmono_checkout_attempt_id": "%s"
                    }
                  }
                }
                """.formatted(attemptId)
        );

        Subscription saved = subscriptionCaptor.getValue();
        assertThat(saved.getBillingProvider()).isEqualTo(BillingProvider.WHOP);
        assertThat(saved.getProviderSubscriptionId()).isEqualTo("mem_123");
        assertThat(saved.getProviderCustomerId()).isEqualTo("member_123");
        assertThat(saved.getProviderUserId()).isEqualTo("whop_user_123");
        assertThat(saved.getProviderProductId()).isEqualTo("prod_monthly");
        assertThat(saved.getProviderPlanId()).isEqualTo("plan_monthly");
        assertThat(saved.getProviderManageUrl()).isEqualTo("https://whop.com/hub/memberships/mem_123");
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getCurrentPeriodStart()).isEqualTo(toLocalDateTime("2027-01-15T08:00:00Z"));
        assertThat(saved.getCurrentPeriodEnd()).isEqualTo(toLocalDateTime("2027-02-14T08:00:00Z"));
        assertThat(saved.getNextBillingDate()).isEqualTo(toLocalDateTime("2027-02-14T08:00:00Z"));
        assertThat(saved.getCancelAtPeriodEnd()).isFalse();
    }

    @Test
    void shouldCreateActiveWhopSubscriptionFromIdentityLockedCheckoutAttemptMetadataWithoutEmail() {
        allowLedgerSaves();
        User user = user("buyer@example.com");
        Product product = product();
        ProductPrice price = ProductPrice.builder()
            .id(UUID.randomUUID())
            .product(product)
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_monthly")
            .isActive(true)
            .build();
        product.addPrice(price);
        UUID attemptId = UUID.randomUUID();
        WhopCheckoutAttempt attempt = WhopCheckoutAttempt.builder()
            .id(attemptId)
            .user(user)
            .productPrice(price)
            .whopPlanId("plan_monthly")
            .status(WhopCheckoutAttemptStatus.CREATED)
            .buyerIdentityLocked(true)
            .lockedBuyerEmail("buyer@example.com")
            .buyerIdentityLockMethod("HOSTED_URL_EMAIL_HIDDEN")
            .build();

        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_attempt_active"))
            .thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.findByIdAndStatus(attemptId, WhopCheckoutAttemptStatus.CREATED))
            .thenReturn(Optional.of(attempt));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_attempt"))
            .thenReturn(Optional.empty());

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        whopWebhookService.handleWebhook(
            "msg_attempt_active",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership.activated",
                  "data": {
                    "id": "mem_attempt",
                    "status": "active",
                    "member": {"id": "member_attempt"},
                    "plan": {"id": "plan_monthly"},
                    "metadata": {
                      "marketmono_checkout_attempt_id": "%s"
                    }
                  }
                }
                """.formatted(attemptId)
        );

        Subscription saved = subscriptionCaptor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getProviderPlanId()).isEqualTo("plan_monthly");
        assertThat(attempt.getStatus()).isEqualTo(WhopCheckoutAttemptStatus.COMPLETED);
        assertThat(attempt.getWhopMembershipId()).isEqualTo("mem_attempt");
        verify(whopCheckoutAttemptRepository).save(attempt);
    }

    @Test
    void shouldHoldLegacyCheckoutAttemptForReviewWhenBuyerEmailIsMissing() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        User user = user("buyer@example.com");
        Product product = product();
        ProductPrice price = ProductPrice.builder()
            .id(UUID.randomUUID())
            .product(product)
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_monthly")
            .isActive(true)
            .build();
        product.addPrice(price);
        UUID attemptId = UUID.randomUUID();
        WhopCheckoutAttempt attempt = WhopCheckoutAttempt.builder()
            .id(attemptId)
            .user(user)
            .productPrice(price)
            .whopPlanId("plan_monthly")
            .status(WhopCheckoutAttemptStatus.CREATED)
            .build();

        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_legacy_missing_email"))
            .thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.findByIdAndStatus(attemptId, WhopCheckoutAttemptStatus.CREATED))
            .thenReturn(Optional.of(attempt));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_legacy"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_legacy_missing_email",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership.activated",
                  "data": {
                    "id": "mem_legacy",
                    "status": "active",
                    "member": {"id": "member_legacy"},
                    "plan": {"id": "plan_monthly"},
                    "metadata": {
                      "marketmono_checkout_attempt_id": "%s"
                    }
                  }
                }
                """.formatted(attemptId)
        );

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        assertThat(attempt.getStatus()).isEqualTo(WhopCheckoutAttemptStatus.CREATED);
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.PENDING_EMAIL_UNAVAILABLE);
    }

    @Test
    void shouldRejectCheckoutAttemptWhenWhopBuyerEmailDiffersFromAttemptUser() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        User user = user("helenzbaker@gmail.com");
        Product product = product();
        ProductPrice price = ProductPrice.builder()
            .id(UUID.randomUUID())
            .product(product)
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_monthly")
            .isActive(true)
            .build();
        product.addPrice(price);
        UUID attemptId = UUID.randomUUID();
        WhopCheckoutAttempt attempt = WhopCheckoutAttempt.builder()
            .id(attemptId)
            .user(user)
            .productPrice(price)
            .whopPlanId("plan_monthly")
            .status(WhopCheckoutAttemptStatus.CREATED)
            .buyerIdentityLocked(true)
            .lockedBuyerEmail("helenzbaker@gmail.com")
            .buyerIdentityLockMethod("HOSTED_URL_EMAIL_HIDDEN")
            .build();

        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_buyer_mismatch"))
            .thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.findByIdAndStatus(attemptId, WhopCheckoutAttemptStatus.CREATED))
            .thenReturn(Optional.of(attempt));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_mismatch"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_buyer_mismatch",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership.activated",
                  "data": {
                    "id": "mem_mismatch",
                    "status": "trialing",
                    "user": {"id": "user_tIPLP1qYfMDVc", "email": "support@marketmono.com"},
                    "member": {"id": "mber_HEracLwaOz2qF"},
                    "product": {"id": "prod_VYg1i2OBMF2No"},
                    "plan": {"id": "plan_monthly"},
                    "metadata": {
                      "marketmono_checkout_attempt_id": "%s",
                      "marketmono_user_email": "helenzbaker@gmail.com"
                    }
                  }
                }
                """.formatted(attemptId)
        );

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        assertThat(attempt.getStatus()).isEqualTo(WhopCheckoutAttemptStatus.CREATED);
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.BUYER_EMAIL_MISMATCH);
        assertLatestCorrelationReasonContains("buyer-email-mismatch");
    }

    @Test
    void shouldCreateDirectBetaSubscriptionWhenBuyerEmailMatchesInvitedActiveUser() {
        allowLedgerSaves();
        User user = betaInvitedUser("buyer@example.com");
        Product product = betaProduct();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_beta_active"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findByWhopPlanId("plan_beta")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_beta"))
            .thenReturn(Optional.empty());

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        whopWebhookService.handleWebhook(
            "msg_beta_active",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_beta",
                    "status": "active",
                    "user": {"id": "whop_user_beta", "email": "buyer@example.com"},
                    "product": {"id": "prod_xKyE6lcOIYmw7"},
                    "plan": {"id": "plan_beta"}
                  }
                }
                """
        );

        Subscription saved = subscriptionCaptor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getProviderPlanId()).isEqualTo("plan_beta");
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.MATCHED);
    }

    @Test
    void shouldCreateDirectBetaSubscriptionWhenBuyerEmailMatchesInvitedWaitlistedUser() {
        allowLedgerSaves();
        User user = betaInvitedUser("buyer@example.com");
        user.setEnabled(false);
        Product product = betaProduct();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_beta_waitlisted"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findByWhopPlanId("plan_beta")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_beta_waitlisted"))
            .thenReturn(Optional.empty());

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        whopWebhookService.handleWebhook(
            "msg_beta_waitlisted",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_beta_waitlisted",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "plan": {"id": "plan_beta"}
                  }
                }
                """
        );

        Subscription saved = subscriptionCaptor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getProduct()).isEqualTo(product);
    }

    @Test
    void shouldRejectDirectBetaSubscriptionWhenBuyerEmailIsNotInvited() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        User user = user("buyer@example.com");
        user.setEnabled(true);
        Product product = betaProduct();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_beta_uninvited"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findByWhopPlanId("plan_beta")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_beta"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_beta_uninvited",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_beta",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "plan": {"id": "plan_beta"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.UNAUTHORIZED_EMAIL);
    }

    @Test
    void shouldHoldDirectBetaSubscriptionForReviewWhenPlanMappingIsMissing() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        Product product = betaProduct();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_beta_no_plan"))
            .thenReturn(Optional.empty());
        when(productRepository.findByWhopProductId("prod_xKyE6lcOIYmw7")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_beta_no_plan"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_beta_no_plan",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_beta_no_plan",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "product": {"id": "prod_xKyE6lcOIYmw7"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.UNMAPPED_PLAN);
    }

    @Test
    void shouldHoldDirectBetaSubscriptionForReviewWhenBuyerEmailIsMissing() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        Product product = betaProduct();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_beta_missing_email"))
            .thenReturn(Optional.empty());
        when(productRepository.findByWhopPlanId("plan_beta")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_beta_missing_email"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_beta_missing_email",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_beta_missing_email",
                    "status": "active",
                    "plan": {"id": "plan_beta"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.PENDING_EMAIL_UNAVAILABLE);
    }

    @Test
    void shouldHoldDirectBetaSubscriptionForReviewWhenBuyerEmailHasNoLocalUser() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        Product product = betaProduct();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_beta_no_user"))
            .thenReturn(Optional.empty());
        when(productRepository.findByWhopPlanId("plan_beta")).thenReturn(Optional.of(product));
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_beta_no_user"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_beta_no_user",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_beta_no_user",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "plan": {"id": "plan_beta"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.PENDING_NO_USER);
    }

    @Test
    void shouldHoldPublicWhopActivationForReviewWhenCheckoutAttemptIsMissing() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        Product product = product();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_public_no_attempt"))
            .thenReturn(Optional.empty());
        when(productRepository.findByWhopPlanId("plan_monthly")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_public_no_attempt"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_public_no_attempt",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_public_no_attempt",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "plan": {"id": "plan_monthly"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.PUBLIC_NO_ATTEMPT);
    }

    @Test
    void shouldHoldHiddenWhopActivationForReview() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        Product product = product();
        product.setVisibility(ProductVisibility.HIDDEN);
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_hidden_product"))
            .thenReturn(Optional.empty());
        when(productRepository.findByWhopPlanId("plan_hidden")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_hidden"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_hidden_product",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_hidden",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "plan": {"id": "plan_hidden"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.REJECTED_PRODUCT);
    }

    @Test
    void shouldHoldInactiveWhopActivationForReview() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        Product product = product();
        product.setIsActive(false);
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_inactive_product"))
            .thenReturn(Optional.empty());
        when(productRepository.findByWhopPlanId("plan_inactive")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_inactive"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_inactive_product",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_inactive",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "plan": {"id": "plan_inactive"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.REJECTED_PRODUCT);
    }

    @Test
    void shouldHoldArchivedWhopActivationForReview() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = allowLedgerSaves();
        Product product = product();
        product.setIsArchived(true);
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_archived_product"))
            .thenReturn(Optional.empty());
        when(productRepository.findByWhopPlanId("plan_archived")).thenReturn(Optional.of(product));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_archived"))
            .thenReturn(Optional.empty());

        whopWebhookService.handleWebhook(
            "msg_archived_product",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "membership_activated",
                  "data": {
                    "id": "mem_archived",
                    "status": "active",
                    "user": {"email": "buyer@example.com"},
                    "plan": {"id": "plan_archived"}
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
        assertLatestLedgerStatus(ledgerCaptor, BillingWebhookEvent.STATUS_NEEDS_REVIEW);
        assertLatestCorrelationStatus(WhopMembershipCorrelationStatus.REJECTED_PRODUCT);
    }

    @Test
    void shouldCancelExistingWhopSubscriptionWhenMembershipDeactivated() {
        allowLedgerSaves();
        Subscription existing = Subscription.builder()
            .id(UUID.randomUUID())
            .billingProvider(BillingProvider.WHOP)
            .providerSubscriptionId("mem_123")
            .status(SubscriptionStatus.ACTIVE)
            .build();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_member_inactive"))
            .thenReturn(Optional.empty());
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_123"))
            .thenReturn(Optional.of(existing));

        whopWebhookService.handleWebhook(
            "msg_member_inactive",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "event_type": "membership_deactivated",
                  "data": {
                    "id": "mem_123",
                    "deactivation_reason": "payment_failed"
                  }
                }
                """
        );

        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(existing.getCanceledAt()).isNotNull();
        assertThat(existing.getCancellationReason()).isEqualTo("payment_failed");
        verify(subscriptionRepository).save(existing);
    }

    @Test
    void shouldRevokePendingWhopCorrelationWhenMembershipDeactivatesBeforeUserExists() {
        allowLedgerSaves();
        WhopMembershipCorrelation pendingCorrelation = WhopMembershipCorrelation.builder()
            .id(UUID.randomUUID())
            .whopMembershipId("mem_pending")
            .buyerEmail("buyer@example.com")
            .status(WhopMembershipCorrelationStatus.PENDING_NO_USER)
            .reviewReason("No MarketMono user matched Whop membership email")
            .build();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(
            BillingProvider.WHOP,
            "msg_pending_deactivated"
        )).thenReturn(Optional.empty());
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_pending"))
            .thenReturn(Optional.empty());
        when(whopMembershipCorrelationRepository.findByWhopMembershipId("mem_pending"))
            .thenReturn(Optional.of(pendingCorrelation));

        whopWebhookService.handleWebhook(
            "msg_pending_deactivated",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "event_type": "membership_deactivated",
                  "data": {
                    "id": "mem_pending",
                    "deactivation_reason": "payment_failed"
                  }
                }
                """
        );

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        verify(whopMembershipCorrelationRepository).save(pendingCorrelation);
        assertThat(pendingCorrelation.getStatus()).isEqualTo(WhopMembershipCorrelationStatus.REVOKED);
        assertThat(pendingCorrelation.getReviewReason()).isEqualTo("membership_deactivated");
    }

    @Test
    void shouldRecordWhopPaymentHistoryOnceWhenPaymentSucceeds() {
        allowLedgerSaves();
        User user = user("buyer@example.com");
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .user(user)
            .billingProvider(BillingProvider.WHOP)
            .providerSubscriptionId("mem_123")
            .status(SubscriptionStatus.ACTIVE)
            .build();
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_payment"))
            .thenReturn(Optional.empty());
        when(paymentHistoryRepository.existsByBillingProviderAndProviderPaymentId(BillingProvider.WHOP, "pay_123"))
            .thenReturn(false);
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(BillingProvider.WHOP, "mem_123"))
            .thenReturn(Optional.of(subscription));

        whopWebhookService.handleWebhook(
            "msg_payment",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            """
                {
                  "type": "payment.succeeded",
                  "data": {
                    "id": "pay_123",
                    "membership": {"id": "mem_123"},
                    "user": {"email": "buyer@example.com"},
                    "total": 40.0,
                    "currency": "usd",
                    "paid_at": 1800000100,
                    "period_start": 1800000000,
                    "period_end": 1802592000
                  }
                }
                """
        );

        verify(paymentHistoryRepository).save(argThatPayment(
            "pay_123",
            subscription,
            user,
            new BigDecimal("40.00")
        ));
    }

    @Test
    void shouldIgnoreDuplicateWebhookDeliveryBeforeMutatingState() {
        when(billingWebhookEventRepository.findByBillingProviderAndExternalEventId(BillingProvider.WHOP, "msg_duplicate"))
            .thenReturn(Optional.of(BillingWebhookEvent.builder()
                .billingProvider(BillingProvider.WHOP)
                .externalEventId("msg_duplicate")
                .eventType("membership_activated")
                .build()));

        whopWebhookService.handleWebhook(
            "msg_duplicate",
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            "{\"type\":\"membership_activated\",\"data\":{\"id\":\"mem_123\"}}"
        );

        verify(userRepository, never()).findByEmail(any());
        verify(productRepository, never()).findByWhopPlanId(any());
        verify(subscriptionRepository, never()).save(any());
        verify(paymentHistoryRepository, never()).save(any());
    }

    private static com.marketmono.entity.PaymentHistory argThatPayment(
            String providerPaymentId,
            Subscription subscription,
            User user,
            BigDecimal amount) {
        return org.mockito.ArgumentMatchers.argThat(paymentHistory ->
            BillingProvider.WHOP == paymentHistory.getBillingProvider()
                && providerPaymentId.equals(paymentHistory.getProviderPaymentId())
                && ("whop:" + providerPaymentId).equals(paymentHistory.getStripeInvoiceId())
                && subscription.equals(paymentHistory.getSubscription())
                && user.equals(paymentHistory.getUser())
                && amount.compareTo(paymentHistory.getAmount()) == 0
                && "USD".equals(paymentHistory.getCurrency())
        );
    }

    private static User user(String email) {
        return User.builder()
            .id(UUID.randomUUID())
            .email(email)
            .emailLowercase(email)
            .build();
    }

    private static Product product() {
        return Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono")
            .slug("market-mono")
            .build();
    }

    private static Product betaProduct() {
        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono Beta")
            .slug("market-mono-beta")
            .isActive(true)
            .isArchived(false)
            .visibility(ProductVisibility.DIRECT_LINK_ONLY)
            .build();
        product.addPrice(ProductPrice.builder()
            .id(UUID.randomUUID())
            .product(product)
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_beta")
            .isActive(true)
            .build());
        return product;
    }

    private static User betaInvitedUser(String email) {
        User user = user(email);
        user.setEnabled(true);
        user.setBetaUser(true);
        user.setBetaAccess(UserBetaAccess.builder()
            .betaInvitedAt(LocalDateTime.now().minusDays(1))
            .betaPromoCode("BETAMM2026")
            .usedBetaPromo(false)
            .build());
        return user;
    }

    private static LocalDateTime toLocalDateTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    private static LocalDateTime toLocalDateTime(String instant) {
        return Instant.parse(instant)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    private static void assertLatestLedgerStatus(
            ArgumentCaptor<BillingWebhookEvent> ledgerCaptor,
            String expectedStatus) {
        assertThat(ledgerCaptor.getAllValues()).isNotEmpty();
        assertThat(ledgerCaptor.getAllValues().get(ledgerCaptor.getAllValues().size() - 1).getProcessingStatus())
            .isEqualTo(expectedStatus);
    }

    private void assertLatestCorrelationStatus(WhopMembershipCorrelationStatus expectedStatus) {
        ArgumentCaptor<WhopMembershipCorrelation> captor = ArgumentCaptor.forClass(WhopMembershipCorrelation.class);
        verify(whopMembershipCorrelationRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus())
            .isEqualTo(expectedStatus);
    }

    private void assertLatestCorrelationReasonContains(String expectedReasonFragment) {
        ArgumentCaptor<WhopMembershipCorrelation> captor = ArgumentCaptor.forClass(WhopMembershipCorrelation.class);
        verify(whopMembershipCorrelationRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getReviewReason())
            .contains(expectedReasonFragment);
    }

    private ArgumentCaptor<BillingWebhookEvent> allowLedgerSaves() {
        ArgumentCaptor<BillingWebhookEvent> ledgerCaptor = ArgumentCaptor.forClass(BillingWebhookEvent.class);
        when(billingWebhookEventRepository.save(ledgerCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return ledgerCaptor;
    }
}
