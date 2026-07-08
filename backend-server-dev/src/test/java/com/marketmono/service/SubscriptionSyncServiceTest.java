package com.marketmono.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.Product;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import com.stripe.model.Invoice;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSyncServiceTest {

  private static final long PERIOD_START_EPOCH = Instant.now().plusSeconds(7L * 24 * 60 * 60).getEpochSecond();
  private static final long PERIOD_END_EPOCH = Instant.now().plusSeconds(38L * 24 * 60 * 60).getEpochSecond();

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SubscriberCapService subscriberCapService;

    @InjectMocks
    private SubscriptionSyncService subscriptionSyncService;

    @Test
    void shouldMapScheduledCancellationFromStripeCancelAt() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("nosub@example.com")
            .emailLowercase("nosub@example.com")
            .build();

        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono Monthly")
            .slug("market-mono-monthly")
            .build();

        com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
        when(stripeSubscription.getId()).thenReturn("sub_test");
        when(stripeSubscription.getCustomer()).thenReturn("cus_test");
        when(stripeSubscription.getStatus()).thenReturn("active");
        when(stripeSubscription.getLivemode()).thenReturn(false);
        when(stripeSubscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(stripeSubscription.getTrialStart()).thenReturn(null);
        when(stripeSubscription.getTrialEnd()).thenReturn(null);
        when(stripeSubscription.getEndedAt()).thenReturn(null);
        when(stripeSubscription.getRawJsonObject()).thenReturn(subscriptionJson());

        when(subscriptionRepository.findByStripeSubscriptionId("sub_test")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerIdTestOrStripeCustomerIdProd("cus_test", "cus_test"))
            .thenReturn(Optional.of(user));
        when(productRepository.findByStripePriceId("price_test")).thenReturn(Optional.of(product));
        when(subscriberCapService.canSyncStripeSubscription(stripeSubscription, user)).thenReturn(true);

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionSyncService.syncSubscription(stripeSubscription);

        Subscription saved = subscriptionCaptor.getValue();
        LocalDateTime expectedStart = toLocalDateTime(PERIOD_START_EPOCH);
        LocalDateTime expectedEnd = toLocalDateTime(PERIOD_END_EPOCH);

        assertThat(saved.getStripeMode()).isEqualTo(StripeMode.TEST);
        assertThat(saved.getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getPriceAtSubscription()).isEqualByComparingTo("40.00");
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getCancelAtPeriodEnd()).isTrue();
        assertThat(saved.getCancellationReason()).isEqualTo("cancellation_requested");
        assertThat(saved.getCurrentPeriodStart()).isEqualTo(expectedStart);
        assertThat(saved.getCurrentPeriodEnd()).isEqualTo(expectedEnd);
        assertThat(saved.getNextBillingDate()).isEqualTo(expectedEnd);
    }

    @Test
    void shouldUpdateInvoiceStatusUsingNestedSubscriptionAndCoveragePeriod() {
        Subscription existingSubscription = Subscription.builder()
            .id(UUID.randomUUID())
            .status(SubscriptionStatus.INCOMPLETE)
            .billingPeriod(BillingPeriod.MONTHLY)
            .stripeMode(StripeMode.TEST)
            .currency("USD")
            .build();

        Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn("in_test");
        when(invoice.getStatus()).thenReturn("paid");
        when(invoice.getRawJsonObject()).thenReturn(invoiceJson());
        when(invoice.getLines()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_test"))
            .thenReturn(Optional.of(existingSubscription));

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionSyncService.updateInvoiceStatus(invoice);

        Subscription saved = subscriptionCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getCurrentPeriodStart()).isEqualTo(toLocalDateTime(PERIOD_START_EPOCH));
        assertThat(saved.getCurrentPeriodEnd()).isEqualTo(toLocalDateTime(PERIOD_END_EPOCH));
        assertThat(saved.getNextBillingDate()).isEqualTo(toLocalDateTime(PERIOD_END_EPOCH));
    }

    @Test
    void shouldNotActivateExistingSubscriptionWhenPaidCapRejectsTransition() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("pending@example.com")
            .emailLowercase("pending@example.com")
            .build();

        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono Monthly")
            .slug("market-mono-monthly")
            .build();

        Subscription existingSubscription = Subscription.builder()
            .id(UUID.randomUUID())
            .status(SubscriptionStatus.INCOMPLETE)
            .billingPeriod(BillingPeriod.MONTHLY)
            .stripeMode(StripeMode.PROD)
            .currency("USD")
            .build();

        com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
        when(stripeSubscription.getId()).thenReturn("sub_live");
        when(stripeSubscription.getCustomer()).thenReturn("cus_live");
        when(stripeSubscription.getRawJsonObject()).thenReturn(subscriptionJson());

        when(subscriptionRepository.findByStripeSubscriptionId("sub_live"))
            .thenReturn(Optional.of(existingSubscription));
        when(userRepository.findByStripeCustomerIdTestOrStripeCustomerIdProd("cus_live", "cus_live"))
            .thenReturn(Optional.of(user));
        when(productRepository.findByStripePriceId("price_test")).thenReturn(Optional.of(product));
        when(subscriberCapService.canSyncStripeSubscription(stripeSubscription, user))
            .thenReturn(false);

        subscriptionSyncService.syncSubscription(stripeSubscription);

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        verify(subscriberCapService, never()).consumeReservationFromStripeSubscription(any(), any());
    }

    @Test
    void shouldMarkBetaPromoUsedWhenSyncedSubscriptionCarriesBetaMetadata() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("beta@example.com")
            .emailLowercase("beta@example.com")
            .betaUser(true)
            .build();

        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono Monthly")
            .slug("market-mono-monthly")
            .build();

        com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
        when(stripeSubscription.getId()).thenReturn("sub_beta");
        when(stripeSubscription.getCustomer()).thenReturn("cus_beta");
        when(stripeSubscription.getStatus()).thenReturn("trialing");
        when(stripeSubscription.getLivemode()).thenReturn(false);
        when(stripeSubscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(stripeSubscription.getTrialStart()).thenReturn(null);
        when(stripeSubscription.getTrialEnd()).thenReturn(null);
        when(stripeSubscription.getEndedAt()).thenReturn(null);
        when(stripeSubscription.getMetadata()).thenReturn(Map.of(
            "beta_user", "true",
            "beta_promo_code", "BETAMM2026"
        ));
        when(stripeSubscription.getRawJsonObject()).thenReturn(subscriptionJson());

        when(subscriptionRepository.findByStripeSubscriptionId("sub_beta")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerIdTestOrStripeCustomerIdProd("cus_beta", "cus_beta"))
            .thenReturn(Optional.of(user));
        when(productRepository.findByStripePriceId("price_test")).thenReturn(Optional.of(product));
        when(subscriberCapService.canSyncStripeSubscription(stripeSubscription, user)).thenReturn(true);
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionSyncService.syncSubscription(stripeSubscription);

        UserBetaAccess betaAccess = user.getBetaAccess();
        assertThat(betaAccess).isNotNull();
        assertThat(betaAccess.getUsedBetaPromo()).isTrue();
        assertThat(betaAccess.getBetaPromoCode()).isEqualTo("BETAMM2026");
        assertThat(betaAccess.getBetaPromoAppliedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void shouldIgnoreMissingSubscriptionCancelWebhook() {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_missing"))
            .thenReturn(Optional.empty());

        subscriptionSyncService.cancelSubscription("sub_missing");

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    private JsonObject subscriptionJson() {
        return JsonParser.parseString(("""
            {
              "cancel_at": %d,
              "cancellation_details": {
                "reason": "cancellation_requested"
              },
              "items": {
                "data": [
                  {
                    "current_period_start": %d,
                    "current_period_end": %d,
                    "price": {
                      "id": "price_test",
                      "currency": "usd",
                      "unit_amount": 4000,
                      "recurring": {
                        "interval": "month",
                        "interval_count": 1
                      }
                    }
                  }
                ]
              }
            }
            """).formatted(PERIOD_END_EPOCH, PERIOD_START_EPOCH, PERIOD_END_EPOCH))
            .getAsJsonObject();
    }

    private JsonObject invoiceJson() {
        return JsonParser.parseString(("""
            {
              "parent": {
                "subscription_details": {
                  "subscription": "sub_test"
                }
              },
              "lines": {
                "data": [
                  {
                    "period": {
                      "start": %d,
                      "end": %d
                    }
                  }
                ]
              }
            }
            """).formatted(PERIOD_START_EPOCH, PERIOD_END_EPOCH))
            .getAsJsonObject();
    }

    private LocalDateTime toLocalDateTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
}
