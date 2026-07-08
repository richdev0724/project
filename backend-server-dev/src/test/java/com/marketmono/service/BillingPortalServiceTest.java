package com.marketmono.service;

import com.marketmono.client.WhopMembershipClient;
import com.marketmono.client.WhopMembershipResponse;
import com.marketmono.dto.BillingPortalResponse;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingPortalServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private StripePortalService stripePortalService;

    @Mock
    private WhopMembershipClient whopMembershipClient;

    @InjectMocks
    private BillingPortalService billingPortalService;

    @Test
    void shouldCreateStripePortalOnlyForActiveStripeBackedSubscription() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .billingProvider(BillingProvider.STRIPE)
            .stripeSubscriptionId("sub_existing")
            .status(SubscriptionStatus.ACTIVE)
            .billingPeriod(BillingPeriod.MONTHLY)
            .build();

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(subscription));
        when(stripePortalService.createPortalSession(userId, "https://app.example.com/billing"))
            .thenReturn("https://billing.stripe.com/session");

        BillingPortalResponse response = billingPortalService.createPortalSession(
            userId,
            "https://app.example.com/billing"
        );

        assertThat(response.url()).isEqualTo("https://billing.stripe.com/session");
        assertThat(response.billingProvider()).isEqualTo(BillingProvider.STRIPE);
        assertThat(response.source()).isEqualTo("STRIPE_PORTAL");
        verify(whopMembershipClient, never()).retrieveMembership("sub_existing");
    }

    @Test
    void shouldReturnStoredWhopManageUrlForActiveWhopSubscription() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = activeWhopSubscription("https://whop.com/hub/memberships/mem_existing");

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(subscription));

        BillingPortalResponse response = billingPortalService.createPortalSession(userId, null);

        assertThat(response.url()).isEqualTo("https://whop.com/hub/memberships/mem_existing");
        assertThat(response.billingProvider()).isEqualTo(BillingProvider.WHOP);
        assertThat(response.source()).isEqualTo("WHOP_MANAGE_URL");
        verify(stripePortalService, never()).createPortalSession(userId, null);
        verify(whopMembershipClient, never()).retrieveMembership("mem_existing");
    }

    @Test
    void shouldRefreshAndPersistWhopManageUrlWhenStoredUrlIsMissing() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = activeWhopSubscription(null);

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(subscription));
        when(whopMembershipClient.retrieveMembership("mem_existing"))
            .thenReturn(new WhopMembershipResponse("mem_existing", "https://whop.com/hub/memberships/mem_existing"));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);

        BillingPortalResponse response = billingPortalService.createPortalSession(userId, null);

        assertThat(response.url()).isEqualTo("https://whop.com/hub/memberships/mem_existing");
        assertThat(response.billingProvider()).isEqualTo(BillingProvider.WHOP);
        assertThat(response.source()).isEqualTo("WHOP_MEMBERSHIP_REFRESH");
        assertThat(subscription.getProviderManageUrl()).isEqualTo("https://whop.com/hub/memberships/mem_existing");
        verify(subscriptionRepository).save(subscription);
        verify(stripePortalService, never()).createPortalSession(userId, null);
    }

    @Test
    void shouldRejectPortalWhenOnlyStaleStripeCustomerIdExists() {
        UUID userId = UUID.randomUUID();

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> billingPortalService.createPortalSession(userId, null))
            .isInstanceOf(BillingPortalService.BillingPortalUnavailableException.class)
            .hasMessageContaining("No active provider-backed subscription");

        verify(stripePortalService, never()).createPortalSession(userId, null);
    }

    private Subscription activeWhopSubscription(String manageUrl) {
        return Subscription.builder()
            .id(UUID.randomUUID())
            .billingProvider(BillingProvider.WHOP)
            .providerSubscriptionId("mem_existing")
            .providerManageUrl(manageUrl)
            .status(SubscriptionStatus.ACTIVE)
            .billingPeriod(BillingPeriod.MONTHLY)
            .currentPeriodEnd(LocalDateTime.now().plusDays(30))
            .build();
    }
}
