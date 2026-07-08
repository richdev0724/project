package com.marketmono.service;

import com.marketmono.entity.CheckoutProviderOverride;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.SubscriberCapReservation;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StripeConfigService stripeConfigService;

    @Mock
    private StripeCustomerService stripeCustomerService;

    @Mock
    private SubscriberCapService subscriberCapService;

    @InjectMocks
    private StripeCheckoutService stripeCheckoutService;

    @Test
    void shouldDisableStripePromotionCodeBoxForStandardCheckout() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("standard@example.com")
            .checkoutProviderOverride(CheckoutProviderOverride.STRIPE)
            .stripeMode(StripeMode.PROD)
            .betaUser(false)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly"))
            .thenReturn(Optional.empty());
        when(stripeCustomerService.ensureCustomerExists(user)).thenReturn("cus_standard");
        when(stripeConfigService.getSecretKey(StripeMode.PROD)).thenReturn("sk_live_test");
        when(stripeConfigService.getValue(StripeConfig.BETA_PROMO_CODE, "BETAMM2026"))
            .thenReturn("BETAMM2026");

        Session checkoutSession = mock(Session.class);
        when(checkoutSession.getUrl()).thenReturn("https://checkout.stripe.test/session");
        AtomicReference<SessionCreateParams> capturedParams = new AtomicReference<>();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenAnswer(invocation -> {
                    capturedParams.set(invocation.getArgument(0));
                    return checkoutSession;
                });

            String sessionUrl = stripeCheckoutService.createCheckoutSession(
                userId,
                "price_live_monthly",
                null,
                null
            );

            assertThat(sessionUrl).isEqualTo("https://checkout.stripe.test/session");
        }

        SessionCreateParams params = capturedParams.get();
        assertThat(params).isNotNull();
        assertThat(params.getAllowPromotionCodes()).isFalse();
        assertThat(params.getDiscounts()).isNull();
        assertThat(params.getSubscriptionData()).isNull();
    }

    @Test
    void shouldApplyBetaTrialWhenExplicitBetaPromoCodeRequestedForExistingNonBetaUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("existing@example.com")
            .checkoutProviderOverride(CheckoutProviderOverride.STRIPE)
            .stripeMode(StripeMode.PROD)
            .betaUser(false)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly"))
            .thenReturn(Optional.empty());
        when(stripeCustomerService.ensureCustomerExists(user)).thenReturn("cus_existing");
        when(stripeConfigService.getSecretKey(StripeMode.PROD)).thenReturn("sk_live_test");
        when(stripeConfigService.getValue(StripeConfig.BETA_PROMO_CODE, "BETAMM2026"))
            .thenReturn("BETAMM2026");
        when(stripeConfigService.getValue(StripeConfig.BETA_PROMOTION_CODE_ID))
            .thenReturn(Optional.of("promo_beta"));
        when(stripeConfigService.getValueAsInt(StripeConfig.BETA_TRIAL_DAYS, 3))
            .thenReturn(3);

        Session checkoutSession = mock(Session.class);
        when(checkoutSession.getUrl()).thenReturn("https://checkout.stripe.test/session");
        AtomicReference<SessionCreateParams> capturedParams = new AtomicReference<>();

        try (MockedStatic<Session> sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenAnswer(invocation -> {
                    capturedParams.set(invocation.getArgument(0));
                    return checkoutSession;
                });

            String sessionUrl = stripeCheckoutService.createCheckoutSession(
                userId,
                "price_live_monthly",
                null,
                null,
                "betamm2026"
            );

            assertThat(sessionUrl).isEqualTo("https://checkout.stripe.test/session");
        }

        SessionCreateParams params = capturedParams.get();
        assertThat(params).isNotNull();
        assertThat(params.getAllowPromotionCodes()).isNull();
        assertThat(params.getDiscounts()).singleElement()
            .extracting(SessionCreateParams.Discount::getPromotionCode)
            .isEqualTo("promo_beta");
        assertThat(params.getMetadata())
            .containsEntry("beta_user", "true")
            .containsEntry("beta_promo_code", "BETAMM2026");
        assertThat(params.getSubscriptionData()).isNotNull();
        assertThat(params.getSubscriptionData().getTrialPeriodDays()).isEqualTo(3L);
        assertThat(params.getSubscriptionData().getMetadata())
            .containsEntry("beta_user", "true")
            .containsEntry("beta_promo_code", "BETAMM2026");
    }

    @Test
    void shouldReleaseCapReservationWhenCustomerCreationFails() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("paid@example.com")
            .checkoutProviderOverride(CheckoutProviderOverride.STRIPE)
            .stripeMode(StripeMode.PROD)
            .build();
        SubscriberCapReservation reservation = SubscriberCapReservation.pending(
            user,
            "price_live_monthly",
            StripeMode.PROD,
            100
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly"))
            .thenReturn(Optional.of(reservation));
        when(stripeCustomerService.ensureCustomerExists(user))
            .thenThrow(new IllegalStateException("customer failed"));

        assertThatThrownBy(() ->
            stripeCheckoutService.createCheckoutSession(userId, "price_live_monthly", null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("customer failed");

        verify(subscriberCapService).releaseReservation(reservation);
    }

    @Test
    void shouldFailFastWhenBetaUserRequestsBetaPromoWithoutConfiguredPromotionCodeId() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("beta@example.com")
            .checkoutProviderOverride(CheckoutProviderOverride.STRIPE)
            .stripeMode(StripeMode.PROD)
            .betaUser(true)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly"))
            .thenReturn(Optional.empty());
        when(stripeCustomerService.ensureCustomerExists(user)).thenReturn("cus_beta");
        when(stripeConfigService.getSecretKey(StripeMode.PROD)).thenReturn("sk_live_test");
        when(stripeConfigService.getValue(StripeConfig.BETA_PROMO_CODE, "BETAMM2026"))
            .thenReturn("BETAMM2026");
        when(stripeConfigService.getValue(StripeConfig.BETA_PROMOTION_CODE_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            stripeCheckoutService.createCheckoutSession(
                userId,
                "price_live_monthly",
                null,
                null,
                "betamm2026"
            ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Beta Stripe promotion code ID is not configured");
    }

    @Test
    void shouldRejectDefaultUserBeforeCreatingStripeCustomer() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("default@example.com")
            .checkoutProviderOverride(CheckoutProviderOverride.DEFAULT)
            .stripeMode(StripeMode.PROD)
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            stripeCheckoutService.createCheckoutSession(userId, "price_live_monthly", null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Stripe checkout is not enabled");

        verify(subscriberCapService, never()).reserveCheckoutSlot(any(User.class), any(StripeMode.class), any());
        verify(stripeCustomerService, never()).ensureCustomerExists(any(User.class));
    }
}
