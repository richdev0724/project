package com.marketmono.service;

import com.marketmono.dto.AdminGrantSubscriptionRequest;
import com.marketmono.dto.AdminRevokeSubscriptionRequest;
import com.marketmono.dto.CancelSubscriptionRequest;
import com.marketmono.dto.DowngradeSubscriptionRequest;
import com.marketmono.dto.SubscriptionDto;
import com.marketmono.dto.UpgradeSubscriptionRequest;
import com.marketmono.dto.UserSubscriptionDto;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StripeConfigService stripeConfigService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void shouldReturnNewestActiveSubscriptionWhenMultipleProvidersGrantAccess() {
        UUID userId = UUID.randomUUID();
        Product stripeProduct = product(UUID.randomUUID(), "Market Mono Legacy", 4000);
        Product whopProduct = product(UUID.randomUUID(), "Market Mono", 4000);
        Subscription stripeSubscription = Subscription.builder()
            .id(UUID.randomUUID())
            .product(stripeProduct)
            .status(SubscriptionStatus.ACTIVE)
            .billingProvider(BillingProvider.STRIPE)
            .stripeSubscriptionId("sub_live_existing")
            .billingPeriod(BillingPeriod.MONTHLY)
            .currentPeriodEnd(LocalDateTime.now().plusDays(20))
            .createdAt(LocalDateTime.now().minusDays(5))
            .build();
        Subscription whopSubscription = Subscription.builder()
            .id(UUID.randomUUID())
            .product(whopProduct)
            .status(SubscriptionStatus.ACTIVE)
            .billingProvider(BillingProvider.WHOP)
            .providerSubscriptionId("mem_latest")
            .billingPeriod(BillingPeriod.MONTHLY)
            .currentPeriodEnd(LocalDateTime.now().plusDays(30))
            .createdAt(LocalDateTime.now())
            .build();

        when(stripeConfigService.getPastDueGracePeriodDays()).thenReturn(1);
        when(subscriptionRepository.findActiveSubscriptionsWithGracePeriod(eq(userId), any(LocalDateTime.class)))
            .thenReturn(List.of(whopSubscription, stripeSubscription));

        UserSubscriptionDto result = subscriptionService.getUserActiveSubscription(userId);

        assertThat(result.subscriptionId()).isEqualTo(whopSubscription.getId());
        assertThat(result.billingProvider()).isEqualTo(BillingProvider.WHOP);
        assertThat(result.productName()).isEqualTo("Market Mono");
        assertThat(result.hasAccess()).isTrue();
    }

    @Test
    void shouldUseWebsiteAccessPredicateWithoutPastDueGraceForProtectedAccess() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.hasWebsiteAccessSubscription(userId)).thenReturn(false);

        assertThat(subscriptionService.hasWebsiteAccess(userId)).isFalse();

        verify(subscriptionRepository).hasWebsiteAccessSubscription(userId);
    }

    @Test
    void shouldExposePastDueSubscriptionWithoutWebsiteAccess() {
        UUID userId = UUID.randomUUID();
        Product product = product(UUID.randomUUID(), "Market Mono", 4000);
        Subscription pastDueSubscription = Subscription.builder()
            .id(UUID.randomUUID())
            .product(product)
            .status(SubscriptionStatus.PAST_DUE)
            .billingProvider(BillingProvider.WHOP)
            .billingPeriod(BillingPeriod.MONTHLY)
            .currentPeriodEnd(LocalDateTime.now().plusDays(3))
            .updatedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build();

        when(stripeConfigService.getPastDueGracePeriodDays()).thenReturn(1);
        when(subscriptionRepository.findActiveSubscriptionsWithGracePeriod(eq(userId), any(LocalDateTime.class)))
            .thenReturn(List.of(pastDueSubscription));

        UserSubscriptionDto result = subscriptionService.getUserActiveSubscription(userId);

        assertThat(result.hasActiveSubscription()).isFalse();
        assertThat(result.hasAccess()).isFalse();
        assertThat(result.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void shouldGrantManualSubscriptionWhenUserHasNoActiveSubscription() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("manual@example.com")
            .stripeMode(StripeMode.DEFAULT)
            .stripeCustomerIdTest("cus_test_manual")
            .build();
        Product product = product(productId, "Market Mono Pro", 4000);
        AdminGrantSubscriptionRequest request = new AdminGrantSubscriptionRequest(
            productId,
            BillingPeriod.MONTHLY,
            45,
            "Support grant"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of());
        when(stripeConfigService.getDefaultMode()).thenReturn(StripeMode.TEST);

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture())).thenAnswer(invocation -> {
            Subscription saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        SubscriptionDto result = subscriptionService.grantSubscription(userId, request);

        Subscription saved = subscriptionCaptor.getValue();
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(saved.getStripeMode()).isEqualTo(StripeMode.TEST);
        assertThat(saved.getStripeCustomerId()).isEqualTo("cus_test_manual");
        assertThat(saved.getStripeSubscriptionId()).isNull();
        assertThat(saved.getCancelAtPeriodEnd()).isFalse();
        assertThat(saved.getPriceAtSubscription()).isEqualByComparingTo("40.00");
        assertThat(saved.getCurrentPeriodStart()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(saved.getCurrentPeriodEnd()).isAfter(LocalDateTime.now().plusDays(44));
        assertThat(saved.getCurrentPeriodEnd()).isBefore(LocalDateTime.now().plusDays(46));
    }

    @Test
    void shouldRejectGrantWhenActiveSubscriptionIsStripeManaged() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("stripe-backed@example.com")
            .build();
        Product product = product(productId, "Market Mono Pro", 4000);
        Subscription existing = Subscription.builder()
            .id(UUID.randomUUID())
            .stripeSubscriptionId("sub_live_existing")
            .status(SubscriptionStatus.ACTIVE)
            .billingPeriod(BillingPeriod.MONTHLY)
            .build();
        AdminGrantSubscriptionRequest request = new AdminGrantSubscriptionRequest(
            productId,
            BillingPeriod.MONTHLY,
            30,
            "Support grant"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> subscriptionService.grantSubscription(userId, request))
            .isInstanceOf(SubscriptionService.ExistingSubscriptionException.class)
            .hasMessageContaining("Stripe-managed");

        verify(subscriptionRepository, never()).save(existing);
    }

    @Test
    void shouldRejectGrantWhenProductHasNoActivePriceForBillingPeriod() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("unsupported-period@example.com")
            .build();
        Product product = product(productId, "Market Mono Pro", 4000);
        AdminGrantSubscriptionRequest request = new AdminGrantSubscriptionRequest(
            productId,
            BillingPeriod.ANNUAL,
            365,
            "Support grant"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> subscriptionService.grantSubscription(userId, request))
            .isInstanceOf(SubscriptionService.ProductNotAvailableException.class)
            .hasMessageContaining("No active ANNUAL price");
    }

    @Test
    void shouldRevokeActiveSubscriptionImmediately() {
        UUID userId = UUID.randomUUID();
        Subscription existing = Subscription.builder()
            .id(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingPeriod(BillingPeriod.MONTHLY)
            .cancelAtPeriodEnd(false)
            .currency("USD")
            .build();
        AdminRevokeSubscriptionRequest request = new AdminRevokeSubscriptionRequest("QA revoke");

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(existing));
        when(subscriptionRepository.save(existing)).thenReturn(existing);

        SubscriptionDto result = subscriptionService.revokeSubscription(userId, request);

        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(existing.getCanceledAt()).isNotNull();
        assertThat(existing.getCancellationReason()).isEqualTo("QA revoke");
        assertThat(existing.getCancelAtPeriodEnd()).isFalse();
        assertThat(existing.getPendingProductId()).isNull();
        assertThat(existing.getPendingBillingPeriod()).isNull();
    }

    @Test
    void shouldRejectRevokeWhenActiveSubscriptionIsStripeManaged() {
        UUID userId = UUID.randomUUID();
        Subscription existing = Subscription.builder()
            .id(UUID.randomUUID())
            .stripeSubscriptionId("sub_live_existing")
            .status(SubscriptionStatus.ACTIVE)
            .billingPeriod(BillingPeriod.MONTHLY)
            .build();
        AdminRevokeSubscriptionRequest request = new AdminRevokeSubscriptionRequest("QA revoke");

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> subscriptionService.revokeSubscription(userId, request))
            .isInstanceOf(SubscriptionService.InvalidSubscriptionActionException.class)
            .hasMessageContaining("Stripe-managed");

        verify(subscriptionRepository, never()).save(existing);
    }

    @Test
    void shouldRejectWhopSubscriptionUpgradeWithoutProviderAuthoritativeMutation() {
        UUID userId = UUID.randomUUID();
        Subscription existing = whopSubscription(product(UUID.randomUUID(), "Market Mono Pro", 4000));
        UUID newProductId = UUID.randomUUID();

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> subscriptionService.upgradeSubscription(
            userId,
            new UpgradeSubscriptionRequest(newProductId, BillingPeriod.MONTHLY)
        ))
            .isInstanceOf(SubscriptionService.InvalidSubscriptionActionException.class)
            .hasMessageContaining("Whop-managed");

        verify(productRepository, never()).findById(newProductId);
        verify(subscriptionRepository, never()).save(existing);
    }

    @Test
    void shouldRejectWhopSubscriptionDowngradeWithoutProviderAuthoritativeMutation() {
        UUID userId = UUID.randomUUID();
        Subscription existing = whopSubscription(product(UUID.randomUUID(), "Market Mono Pro", 4000));
        UUID newProductId = UUID.randomUUID();

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> subscriptionService.downgradeSubscription(
            userId,
            new DowngradeSubscriptionRequest(newProductId, BillingPeriod.MONTHLY, "Switch plan")
        ))
            .isInstanceOf(SubscriptionService.InvalidSubscriptionActionException.class)
            .hasMessageContaining("Whop-managed");

        verify(productRepository, never()).findById(newProductId);
        verify(subscriptionRepository, never()).save(existing);
    }

    @Test
    void shouldRejectWhopSubscriptionCancellationWithoutProviderAuthoritativeMutation() {
        UUID userId = UUID.randomUUID();
        Subscription existing = whopSubscription(product(UUID.randomUUID(), "Market Mono Pro", 4000));

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(
            userId,
            new CancelSubscriptionRequest("Cancel in Whop", false)
        ))
            .isInstanceOf(SubscriptionService.InvalidSubscriptionActionException.class)
            .hasMessageContaining("Whop-managed");

        verify(subscriptionRepository, never()).save(existing);
    }

    @Test
    void shouldRejectWhopSubscriptionReactivationWithoutProviderAuthoritativeMutation() {
        UUID userId = UUID.randomUUID();
        Subscription existing = whopSubscription(product(UUID.randomUUID(), "Market Mono Pro", 4000));
        existing.setCancelAtPeriodEnd(true);

        when(subscriptionRepository.findActiveSubscriptionsByUserId(userId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> subscriptionService.reactivateSubscription(userId))
            .isInstanceOf(SubscriptionService.InvalidSubscriptionActionException.class)
            .hasMessageContaining("Whop-managed");

        verify(subscriptionRepository, never()).save(existing);
    }

    private Product product(UUID productId, String name, int monthlyPriceCents) {
        Product product = Product.builder()
            .id(productId)
            .name(name)
            .slug(name.toLowerCase().replace(" ", "-"))
            .isActive(true)
            .isArchived(false)
            .build();
        product.addPrice(ProductPrice.builder()
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(monthlyPriceCents)
            .isActive(true)
            .build());
        return product;
    }

    private Subscription whopSubscription(Product product) {
        return Subscription.builder()
            .id(UUID.randomUUID())
            .product(product)
            .status(SubscriptionStatus.ACTIVE)
            .billingProvider(BillingProvider.WHOP)
            .providerSubscriptionId("mem_existing")
            .providerManageUrl("https://whop.com/hub/membership/mem_existing")
            .billingPeriod(BillingPeriod.MONTHLY)
            .cancelAtPeriodEnd(false)
            .currency("USD")
            .build();
    }
}
