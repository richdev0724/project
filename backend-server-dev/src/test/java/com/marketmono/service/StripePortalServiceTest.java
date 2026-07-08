package com.marketmono.service;

import com.marketmono.entity.StripeMode;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripePortalServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StripeConfigService stripeConfigService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private StripePortalService stripePortalService;

    @Test
    void shouldRejectPortalSessionWhenUserOnlyHasStaleStripeCustomerId() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("legacy@example.com")
            .emailLowercase("legacy@example.com")
            .stripeMode(StripeMode.TEST)
            .stripeCustomerIdTest("cus_stale")
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> stripePortalService.createPortalSession(userId, "https://marketmono.com/billing"))
            .isInstanceOf(StripePortalService.StripePortalNotAvailableException.class)
            .hasMessageContaining("active Stripe subscription");

        verifyNoInteractions(stripeConfigService);
    }
}
