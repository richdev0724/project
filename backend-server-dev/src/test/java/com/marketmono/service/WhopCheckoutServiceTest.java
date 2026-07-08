package com.marketmono.service;

import com.marketmono.client.WhopCheckoutClient;
import com.marketmono.client.WhopCheckoutConfigurationRequest;
import com.marketmono.client.WhopCheckoutConfigurationResponse;
import com.marketmono.config.WhopCheckoutProperties;
import com.marketmono.dto.UserSubscriptionDto;
import com.marketmono.dto.WhopCheckoutSessionResponse;
import com.marketmono.entity.CheckoutProviderOverride;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.SubscriberCapReservation;
import com.marketmono.entity.WhopCheckoutAttempt;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.ProductPriceRepository;
import com.marketmono.repository.WhopCheckoutAttemptRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhopCheckoutServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private SubscriberCapService subscriberCapService;

    @Mock
    private WhopCheckoutAttemptRepository whopCheckoutAttemptRepository;

    @Mock
    private WhopCheckoutClient whopCheckoutClient;

    @Mock
    private BetaWhopCheckoutService betaWhopCheckoutService;

    @Mock
    private SubscriptionService subscriptionService;

    private WhopCheckoutService whopCheckoutService;

    @BeforeEach
    void setUp() {
        WhopCheckoutProperties properties = new WhopCheckoutProperties();
        properties.setApiKey("whop_key");
        properties.setCompanyId("biz_test");
        properties.setRedirectUrl("https://marketmono.com/subscribe?checkout=success");
        properties.setSourceUrl("https://marketmono.com/subscribe");
        properties.setAllowPromoCodes(true);

        whopCheckoutService = new WhopCheckoutService(
            userRepository,
            productPriceRepository,
            subscriberCapService,
            whopCheckoutAttemptRepository,
            whopCheckoutClient,
            properties,
            betaWhopCheckoutService,
            subscriptionService
        );

        lenient()
            .when(subscriptionService.getUserActiveSubscription(any()))
            .thenReturn(UserSubscriptionDto.noSubscription());
        lenient()
            .when(whopCheckoutAttemptRepository
                .findFirstByUser_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                    any(), any(), any()))
            .thenReturn(Optional.empty());
        lenient()
            .when(whopCheckoutAttemptRepository
                .findFirstByUser_IdAndStatusAndCompletedAtAfterOrderByCompletedAtDesc(
                    any(), any(), any()))
            .thenReturn(Optional.empty());
    }

    @Test
    void shouldCreatePerAttemptCheckoutAndNeverReturnStaticWhopUrl() {
        UUID userId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        User user = whopUser(userId);
        ProductPrice price = whopPrice(priceId);
        SubscriberCapReservation reservation = SubscriberCapReservation.pending(
            user,
            priceId.toString(),
            StripeMode.PROD,
            100
        );
        reservation.setId(UUID.randomUUID());
        AtomicReference<UUID> attemptId = new AtomicReference<>();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productPriceRepository.findActiveByIdWithProduct(priceId)).thenReturn(Optional.of(price));
        when(subscriberCapService.reserveWhopCheckoutSlot(user, priceId.toString()))
            .thenReturn(Optional.of(reservation));
        when(whopCheckoutAttemptRepository.save(any(WhopCheckoutAttempt.class)))
            .thenAnswer(invocation -> {
                WhopCheckoutAttempt attempt = invocation.getArgument(0);
                if (attempt.getId() == null) {
                    attempt.setId(UUID.randomUUID());
                }
                attemptId.set(attempt.getId());
                return attempt;
            });

        ArgumentCaptor<WhopCheckoutConfigurationRequest> requestCaptor =
            ArgumentCaptor.forClass(WhopCheckoutConfigurationRequest.class);
        when(whopCheckoutClient.createCheckoutConfiguration(requestCaptor.capture()))
            .thenReturn(new WhopCheckoutConfigurationResponse(
                "ch_generated",
                "https://whop.com/checkout/generated?session=ch_generated"
            ));

        WhopCheckoutSessionResponse response =
            whopCheckoutService.createCheckoutSession(userId, priceId, null, null);

        assertThat(response.purchaseUrl())
            .isEqualTo("https://whop.com/checkout/generated?session=ch_generated&email=buyer%40example.com&email.disabled=1&email.hidden=1")
            .isNotEqualTo("https://whop.com/checkout/static-plan-url");
        assertThat(response.attemptId()).isEqualTo(attemptId.get());
        assertThat(response.sessionId()).isEqualTo("ch_generated");
        assertThat(response.planId()).isEqualTo("plan_whop_monthly");
        assertThat(response.buyerEmail()).isEqualTo("buyer@example.com");
        assertThat(response.lockMethod()).isEqualTo("HOSTED_URL_EMAIL_HIDDEN");

        WhopCheckoutConfigurationRequest whopRequest = requestCaptor.getValue();
        assertThat(whopRequest.companyId()).isEqualTo("biz_test");
        assertThat(whopRequest.planId()).isEqualTo("plan_whop_monthly");
        assertThat(whopRequest.allowPromoCodes()).isTrue();
        assertThat(whopRequest.metadata())
            .containsEntry("marketmono_user_id", userId.toString())
            .containsEntry("marketmono_product_price_id", priceId.toString())
            .containsEntry("marketmono_whop_plan_id", "plan_whop_monthly")
            .containsEntry("marketmono_checkout_attempt_id", attemptId.get().toString())
            .containsEntry("subscriber_cap_reservation_id", reservation.getId().toString());
        ArgumentCaptor<WhopCheckoutAttempt> attemptCaptor = ArgumentCaptor.forClass(WhopCheckoutAttempt.class);
        verify(whopCheckoutAttemptRepository, atLeast(2)).save(attemptCaptor.capture());
        WhopCheckoutAttempt createdAttempt = attemptCaptor.getAllValues().get(attemptCaptor.getAllValues().size() - 1);
        assertThat(createdAttempt.getWhopPurchaseUrl()).isEqualTo(response.purchaseUrl());
        assertThat(createdAttempt.isBuyerIdentityLocked()).isTrue();
        assertThat(createdAttempt.getLockedBuyerEmail()).isEqualTo("buyer@example.com");
        assertThat(createdAttempt.getBuyerIdentityLockMethod()).isEqualTo("HOSTED_URL_EMAIL_HIDDEN");
        verify(subscriberCapService).markWhopCheckoutCreated(reservation, "ch_generated");
    }

    @Test
    void shouldEncodePlusAddressedBuyerEmailInLockedHostedUrl() {
        UUID userId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        User user = whopUser(userId);
        user.setEmail("buyer+03@example.com");
        user.setEmailLowercase("buyer+03@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productPriceRepository.findActiveByIdWithProduct(priceId)).thenReturn(Optional.of(whopPrice(priceId)));
        when(subscriberCapService.reserveWhopCheckoutSlot(user, priceId.toString()))
            .thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.save(any(WhopCheckoutAttempt.class)))
            .thenAnswer(invocation -> {
                WhopCheckoutAttempt attempt = invocation.getArgument(0);
                if (attempt.getId() == null) {
                    attempt.setId(UUID.randomUUID());
                }
                return attempt;
            });
        when(whopCheckoutClient.createCheckoutConfiguration(any()))
            .thenReturn(new WhopCheckoutConfigurationResponse("ch_plus", "https://whop.com/checkout/generated"));

        WhopCheckoutSessionResponse response =
            whopCheckoutService.createCheckoutSession(userId, priceId, null, null);

        assertThat(response.purchaseUrl())
            .isEqualTo("https://whop.com/checkout/generated?email=buyer%2B03%40example.com&email.disabled=1&email.hidden=1");
        assertThat(response.buyerEmail()).isEqualTo("buyer+03@example.com");
    }

    @Test
    void shouldCreatePublicWhopCheckoutForDefaultUsers() {
        UUID userId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        User user = whopUser(userId);
        user.setCheckoutProviderOverride(CheckoutProviderOverride.DEFAULT);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(productPriceRepository.findActiveByIdWithProduct(priceId)).thenReturn(Optional.of(whopPrice(priceId)));
        when(subscriberCapService.reserveWhopCheckoutSlot(user, priceId.toString()))
            .thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.save(any(WhopCheckoutAttempt.class)))
            .thenAnswer(invocation -> {
                WhopCheckoutAttempt attempt = invocation.getArgument(0);
                if (attempt.getId() == null) {
                    attempt.setId(UUID.randomUUID());
                }
                return attempt;
            });
        when(whopCheckoutClient.createCheckoutConfiguration(any()))
            .thenReturn(new WhopCheckoutConfigurationResponse("ch_default", "https://whop.com/checkout/generated"));

        WhopCheckoutSessionResponse response =
            whopCheckoutService.createCheckoutSession(userId, priceId, null, null);

        assertThat(response.purchaseUrl())
            .isEqualTo("https://whop.com/checkout/generated?email=buyer%40example.com&email.disabled=1&email.hidden=1");
        assertThat(response.sessionId()).isEqualTo("ch_default");
        assertThat(response.buyerEmail()).isEqualTo("buyer@example.com");
    }

    @Test
    void shouldRejectDirectLinkOnlyProductFromGenericCheckout() {
        UUID userId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        User user = whopUser(userId);
        ProductPrice price = whopPrice(priceId);
        price.getProduct().setVisibility(ProductVisibility.DIRECT_LINK_ONLY);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productPriceRepository.findActiveByIdWithProduct(priceId)).thenReturn(Optional.of(price));

        assertThatThrownBy(() -> whopCheckoutService.createCheckoutSession(userId, priceId, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not available for public Whop checkout");

        verify(whopCheckoutClient, never()).createCheckoutConfiguration(any());
    }

    @Test
    void shouldCreateIdentityLockedBetaCheckoutForInvitedPromoUser() {
        UUID userId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        User user = whopUser(userId);
        ProductPrice price = whopPrice(priceId);
        price.getProduct().setName("Market Mono Beta");
        price.getProduct().setVisibility(ProductVisibility.DIRECT_LINK_ONLY);

        when(betaWhopCheckoutService.betaCheckoutPriceForPromo(userId, "BETAMM2026"))
            .thenReturn(price);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriberCapService.reserveWhopCheckoutSlot(user, priceId.toString()))
            .thenReturn(Optional.empty());
        when(whopCheckoutAttemptRepository.save(any(WhopCheckoutAttempt.class)))
            .thenAnswer(invocation -> {
                WhopCheckoutAttempt attempt = invocation.getArgument(0);
                if (attempt.getId() == null) {
                    attempt.setId(UUID.randomUUID());
                }
                return attempt;
            });
        when(whopCheckoutClient.createCheckoutConfiguration(any()))
            .thenReturn(new WhopCheckoutConfigurationResponse(
                "ch_beta",
                "https://whop.com/checkout/beta?session=ch_beta"
            ));

        WhopCheckoutSessionResponse response = whopCheckoutService.createBetaCheckoutSession(
            userId,
            "BETAMM2026",
            "https://marketmono.com/",
            "https://marketmono.com/subscribe"
        );

        assertThat(response.purchaseUrl())
            .isEqualTo("https://whop.com/checkout/beta?session=ch_beta&email=buyer%40example.com&email.disabled=1&email.hidden=1");
        assertThat(response.sessionId()).isEqualTo("ch_beta");
        assertThat(response.planId()).isEqualTo("plan_whop_monthly");
        assertThat(response.buyerEmail()).isEqualTo("buyer@example.com");
    }

    private static User whopUser(UUID userId) {
        return User.builder()
            .id(userId)
            .email("buyer@example.com")
            .emailLowercase("buyer@example.com")
            .checkoutProviderOverride(CheckoutProviderOverride.WHOP)
            .enabled(true)
            .build();
    }

    private static ProductPrice whopPrice(UUID priceId) {
        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono")
            .slug("market-mono")
            .isActive(true)
            .isArchived(false)
            .visibility(ProductVisibility.PUBLIC)
            .build();
        ProductPrice price = ProductPrice.builder()
            .id(priceId)
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_whop_monthly")
            .whopPurchaseUrl("https://whop.com/checkout/static-plan-url")
            .isActive(true)
            .build();
        product.addPrice(price);
        return price;
    }
}
