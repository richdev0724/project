package com.marketmono.service;

import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.WhopMembershipCorrelation;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.WhopMembershipCorrelationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhopMembershipReconciliationServiceTest {

    @Mock
    private WhopMembershipCorrelationRepository whopMembershipCorrelationRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private WhopMembershipReconciliationService reconciliationService;

    @Test
    void shouldPromotePendingDirectWhopMembershipWhenUserRegistersWithMatchingEmail() {
        User user = user("Buyer@Example.com");
        Product product = betaProduct();
        WhopMembershipCorrelation correlation = pendingCorrelation(product);

        when(whopMembershipCorrelationRepository.findByBuyerEmailAndStatusForReconciliation(
            "Buyer@Example.com",
            WhopMembershipCorrelationStatus.PENDING_NO_USER
        )).thenReturn(List.of(correlation));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(
            BillingProvider.WHOP,
            "mem_pending"
        )).thenReturn(Optional.empty());
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        when(subscriptionRepository.save(subscriptionCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(whopMembershipCorrelationRepository.save(any(WhopMembershipCorrelation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        int reconciled = reconciliationService.reconcilePendingMemberships(user);

        assertThat(reconciled).isEqualTo(1);
        Subscription subscription = subscriptionCaptor.getValue();
        assertThat(subscription.getBillingProvider()).isEqualTo(BillingProvider.WHOP);
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("mem_pending");
        assertThat(subscription.getProviderUserId()).isEqualTo("whop_user_pending");
        assertThat(subscription.getProviderProductId()).isEqualTo("prod_beta");
        assertThat(subscription.getProviderPlanId()).isEqualTo("plan_beta");
        assertThat(subscription.getUser()).isEqualTo(user);
        assertThat(subscription.getProduct()).isEqualTo(product);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(subscription.getPriceAtSubscription()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(subscription.getCurrentPeriodStart()).isNotNull();
        assertThat(correlation.getStatus()).isEqualTo(WhopMembershipCorrelationStatus.MATCHED);
        assertThat(correlation.getUser()).isEqualTo(user);
        assertThat(correlation.getSubscription()).isEqualTo(subscription);
        assertThat(correlation.getReviewReason()).isNull();
    }

    @Test
    void shouldLinkPendingCorrelationToExistingWhopSubscriptionWithoutCreatingDuplicate() {
        User user = user("buyer@example.com");
        Product product = betaProduct();
        WhopMembershipCorrelation correlation = pendingCorrelation(product);
        Subscription existing = Subscription.builder()
            .id(UUID.randomUUID())
            .billingProvider(BillingProvider.WHOP)
            .providerSubscriptionId("mem_pending")
            .user(user)
            .product(product)
            .status(SubscriptionStatus.ACTIVE)
            .build();

        when(whopMembershipCorrelationRepository.findByBuyerEmailAndStatusForReconciliation(
            "buyer@example.com",
            WhopMembershipCorrelationStatus.PENDING_NO_USER
        )).thenReturn(List.of(correlation));
        when(subscriptionRepository.findByBillingProviderAndProviderSubscriptionId(
            BillingProvider.WHOP,
            "mem_pending"
        )).thenReturn(Optional.of(existing));
        when(whopMembershipCorrelationRepository.save(any(WhopMembershipCorrelation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        int reconciled = reconciliationService.reconcilePendingMemberships(user);

        assertThat(reconciled).isEqualTo(1);
        verify(subscriptionRepository, never()).save(any(Subscription.class));
        assertThat(correlation.getStatus()).isEqualTo(WhopMembershipCorrelationStatus.MATCHED);
        assertThat(correlation.getSubscription()).isEqualTo(existing);
    }

    @Test
    void shouldNotGrantPendingMembershipForPublicProductCorrelation() {
        User user = user("buyer@example.com");
        Product product = betaProduct();
        product.setVisibility(ProductVisibility.PUBLIC);
        WhopMembershipCorrelation correlation = pendingCorrelation(product);

        when(whopMembershipCorrelationRepository.findByBuyerEmailAndStatusForReconciliation(
            "buyer@example.com",
            WhopMembershipCorrelationStatus.PENDING_NO_USER
        )).thenReturn(List.of(correlation));

        int reconciled = reconciliationService.reconcilePendingMemberships(user);

        assertThat(reconciled).isZero();
        verifyNoInteractions(subscriptionRepository);
        verify(whopMembershipCorrelationRepository, never()).save(any(WhopMembershipCorrelation.class));
        assertThat(correlation.getStatus()).isEqualTo(WhopMembershipCorrelationStatus.PENDING_NO_USER);
    }

    private static User user(String email) {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email(email)
            .enabled(true)
            .emailVerified(true)
            .build();
        user.setEmail(email);
        return user;
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
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_beta")
            .isActive(true)
            .build());
        return product;
    }

    private static WhopMembershipCorrelation pendingCorrelation(Product product) {
        return WhopMembershipCorrelation.builder()
            .id(UUID.randomUUID())
            .whopMembershipId("mem_pending")
            .whopUserId("whop_user_pending")
            .buyerEmail("buyer@example.com")
            .whopProductId("prod_beta")
            .whopPlanId("plan_beta")
            .status(WhopMembershipCorrelationStatus.PENDING_NO_USER)
            .reviewReason("No MarketMono user matched Whop membership email")
            .product(product)
            .build();
    }
}
