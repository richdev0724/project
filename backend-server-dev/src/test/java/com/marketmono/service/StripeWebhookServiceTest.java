package com.marketmono.service;

import com.marketmono.entity.PaymentHistory;
import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final long PERIOD_START_EPOCH = 1773732388L;
    private static final long PERIOD_END_EPOCH = 1776410788L;
    private static final long PAID_AT_EPOCH = 1773732389L;

    @Mock
    private StripeConfigService stripeConfigService;

    @Mock
    private ProductSyncService productSyncService;

    @Mock
    private SubscriptionSyncService subscriptionSyncService;

    @Mock
    private ReferralService referralService;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private StripeWebhookService stripeWebhookService;

    @Test
    void shouldCreatePaymentHistoryFromResolvedInvoiceSubscriptionAndCoverage() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("nosub@example.com")
            .emailLowercase("nosub@example.com")
            .stripeCustomerIdTest("cus_test")
            .build();

        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .stripeSubscriptionId("sub_test")
            .stripeMode(StripeMode.TEST)
            .build();

        Invoice invoice = mock(Invoice.class, RETURNS_DEEP_STUBS);
        when(invoice.getId()).thenReturn("in_test");
        when(invoice.getCustomer()).thenReturn("cus_test");
        when(invoice.getCurrency()).thenReturn("usd");
        when(invoice.getAmountPaid()).thenReturn(4000L);
        when(invoice.getStatusTransitions().getPaidAt()).thenReturn(PAID_AT_EPOCH);
        when(invoice.getRawJsonObject()).thenReturn(null);

        when(paymentHistoryRepository.existsByStripeInvoiceId("in_test")).thenReturn(false);
        when(userRepository.findByStripeCustomerIdTestOrStripeCustomerIdProd("cus_test", "cus_test"))
            .thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_test"))
            .thenReturn(Optional.of(subscription));
        when(subscriptionSyncService.resolveSubscriptionIdFromInvoice(invoice)).thenReturn("sub_test");
        when(subscriptionSyncService.resolveInvoiceCoveragePeriod(invoice)).thenReturn(
            new SubscriptionSyncService.InvoiceCoveragePeriod(
                toLocalDateTime(PERIOD_START_EPOCH),
                toLocalDateTime(PERIOD_END_EPOCH)
            )
        );

        ArgumentCaptor<PaymentHistory> paymentHistoryCaptor = ArgumentCaptor.forClass(PaymentHistory.class);
        when(paymentHistoryRepository.save(paymentHistoryCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        stripeWebhookService.createPaymentHistory(invoice, PaymentStatus.PAID, false);

        PaymentHistory saved = paymentHistoryCaptor.getValue();
        assertThat(saved.getStripeInvoiceId()).isEqualTo("in_test");
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getSubscription()).isEqualTo(subscription);
        assertThat(saved.getAmount()).isEqualByComparingTo("40.00");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAt()).isEqualTo(toLocalDateTime(PAID_AT_EPOCH));
        assertThat(saved.getPeriodStart()).isEqualTo(toLocalDateTime(PERIOD_START_EPOCH));
        assertThat(saved.getPeriodEnd()).isEqualTo(toLocalDateTime(PERIOD_END_EPOCH));
        assertThat(saved.getStripeMode()).isEqualTo(StripeMode.TEST);

        verify(referralService).qualifyReferral(user, subscription, "in_test");
    }

    @Test
    void shouldSyncSubscriptionWhenCheckoutSessionCompletes() {
        com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
        StripeWebhookService service = spy(stripeWebhookService);

        doReturn(stripeSubscription).when(service).retrieveSubscription("sub_checkout", false);

        service.syncCheckoutSubscription("sub_checkout", false);

        verify(subscriptionSyncService).syncSubscription(stripeSubscription);
    }

    @Test
    void shouldIgnoreCheckoutSessionWithoutSubscription() {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test");
        when(session.getSubscription()).thenReturn(null);

        stripeWebhookService.handleCheckoutSessionCompleted(session, false);

        verify(subscriptionSyncService, never()).syncSubscription(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSyncMissingSubscriptionBeforeInvoiceStatusUpdate() {
        Invoice invoice = mock(Invoice.class);
        com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
        StripeWebhookService service = spy(stripeWebhookService);

        when(subscriptionSyncService.resolveSubscriptionIdFromInvoice(invoice)).thenReturn("sub_invoice");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_invoice")).thenReturn(Optional.empty());
        doReturn(stripeSubscription).when(service).retrieveSubscription("sub_invoice", false);

        service.ensureSubscriptionSyncedFromInvoice(invoice, false);

        verify(subscriptionSyncService).syncSubscription(stripeSubscription);
    }

    private LocalDateTime toLocalDateTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}
