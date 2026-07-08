package com.marketmono.service;

import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.SubscriberCapReservation;
import com.marketmono.entity.auth.User;
import com.marketmono.exception.SubscriberCapReachedException;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.SubscriberCapReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriberCapServiceTest {

    @Mock
    private StripeConfigService stripeConfigService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriberCapReservationRepository reservationRepository;

    @InjectMocks
    private SubscriberCapService subscriberCapService;

    @Test
    void shouldNotReserveSlotsWhenCapIsDisabled() {
        User user = activeUser("disabled@example.com");
        when(stripeConfigService.getValue(StripeConfig.SUBSCRIBER_CAP_ENABLED, "false"))
            .thenReturn("false");

        Optional<SubscriberCapReservation> reservation =
            subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly");

        assertThat(reservation).isEmpty();
        verify(reservationRepository, never()).lockLaunchCapScope();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldNotReserveSlotsForBetaUsersBecauseTheyAreTestUsers() {
        User user = activeUser("beta@example.com");
        user.setBetaUser(true);
        when(stripeConfigService.getValue(StripeConfig.SUBSCRIBER_CAP_ENABLED, "false"))
            .thenReturn("true");

        Optional<SubscriberCapReservation> reservation =
            subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly");

        assertThat(reservation).isEmpty();
        verify(subscriptionRepository, never()).countRealPaidSubscribersForLaunchCap();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldCreatePendingReservationWhenLivePaidCapHasCapacity() {
        User user = activeUser("paid@example.com");
        when(stripeConfigService.getValue(StripeConfig.SUBSCRIBER_CAP_ENABLED, "false"))
            .thenReturn("true");
        when(stripeConfigService.getValueAsInt(StripeConfig.SUBSCRIBER_CAP_LIMIT, 100))
            .thenReturn(100);
        when(subscriptionRepository.existsRealPaidSubscriberByUserId(user.getId()))
            .thenReturn(false);
        when(subscriptionRepository.countRealPaidSubscribersForLaunchCap()).thenReturn(99L);
        when(reservationRepository.countPendingReservationsForLaunchCap(any(LocalDateTime.class)))
            .thenReturn(0L);
        ArgumentCaptor<SubscriberCapReservation> reservationCaptor =
            ArgumentCaptor.forClass(SubscriberCapReservation.class);
        when(reservationRepository.save(reservationCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<SubscriberCapReservation> reservation =
            subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly");

        assertThat(reservation).isPresent();
        SubscriberCapReservation saved = reservationCaptor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getPriceId()).isEqualTo("price_live_monthly");
        assertThat(saved.getStripeMode()).isEqualTo(StripeMode.PROD);
        assertThat(saved.isPending()).isTrue();
        assertThat(saved.getPolicyLimit()).isEqualTo(100);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(reservationRepository).lockLaunchCapScope();
    }

    @Test
    void shouldRejectLiveCheckoutWhenCapIsReached() {
        User user = activeUser("full@example.com");
        when(stripeConfigService.getValue(StripeConfig.SUBSCRIBER_CAP_ENABLED, "false"))
            .thenReturn("true");
        when(stripeConfigService.getValueAsInt(StripeConfig.SUBSCRIBER_CAP_LIMIT, 100))
            .thenReturn(100);
        when(subscriptionRepository.existsRealPaidSubscriberByUserId(user.getId()))
            .thenReturn(false);
        when(subscriptionRepository.countRealPaidSubscribersForLaunchCap()).thenReturn(100L);
        when(reservationRepository.countPendingReservationsForLaunchCap(any(LocalDateTime.class)))
            .thenReturn(0L);

        assertThatThrownBy(() ->
            subscriberCapService.reserveCheckoutSlot(user, StripeMode.PROD, "price_live_monthly")
        )
            .isInstanceOf(SubscriberCapReachedException.class)
            .hasMessageContaining("paid subscriber cap");

        verify(reservationRepository).lockLaunchCapScope();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldNotConsumeReservationUntilLiveStripeSubscriptionIsActive() {
        User user = activeUser("incomplete@example.com");
        com.stripe.model.Subscription stripeSubscription = mock(com.stripe.model.Subscription.class);
        when(stripeSubscription.getLivemode()).thenReturn(true);
        when(stripeSubscription.getStatus()).thenReturn("incomplete");

        subscriberCapService.consumeReservationFromStripeSubscription(stripeSubscription, user);

        verify(reservationRepository, never()).findByIdAndStatus(any(), any());
        verify(reservationRepository, never()).save(any());
    }

    private User activeUser(String email) {
        return User.builder()
            .id(UUID.randomUUID())
            .email(email)
            .emailLowercase(email)
            .enabled(true)
            .build();
    }
}
