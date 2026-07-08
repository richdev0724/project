package com.marketmono.service;

import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.entity.WhopMembershipCorrelation;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.WhopMembershipCorrelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhopMembershipReconciliationService {

    private static final String DEFAULT_CURRENCY = "USD";

    private final WhopMembershipCorrelationRepository whopMembershipCorrelationRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public int reconcilePendingMemberships(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return 0;
        }

        List<WhopMembershipCorrelation> pendingCorrelations = whopMembershipCorrelationRepository
            .findByBuyerEmailAndStatusForReconciliation(
                user.getEmail(),
                WhopMembershipCorrelationStatus.PENDING_NO_USER
            );

        int reconciled = 0;
        for (WhopMembershipCorrelation correlation : pendingCorrelations) {
            if (reconcileCorrelation(user, correlation)) {
                reconciled++;
            }
        }
        return reconciled;
    }

    private boolean reconcileCorrelation(User user, WhopMembershipCorrelation correlation) {
        Product product = correlation.getProduct();
        if (!canGrantProduct(product) || correlation.getWhopMembershipId() == null) {
            return false;
        }

        Subscription subscription = subscriptionRepository
            .findByBillingProviderAndProviderSubscriptionId(
                BillingProvider.WHOP,
                correlation.getWhopMembershipId()
            )
            .orElseGet(() -> createSubscription(user, product, correlation));

        correlation.setUser(user);
        correlation.setProduct(product);
        correlation.setSubscription(subscription);
        correlation.setStatus(WhopMembershipCorrelationStatus.MATCHED);
        correlation.setReviewReason(null);
        whopMembershipCorrelationRepository.save(correlation);

        log.info(
            "Reconciled pending Whop membership {} to user {}",
            correlation.getWhopMembershipId(),
            user.getEmail()
        );
        return true;
    }

    private Subscription createSubscription(
            User user,
            Product product,
            WhopMembershipCorrelation correlation) {
        LocalDateTime now = LocalDateTime.now();
        ProductPrice price = priceForPlan(product, correlation.getWhopPlanId());

        Subscription subscription = new Subscription();
        subscription.setBillingProvider(BillingProvider.WHOP);
        subscription.setProviderSubscriptionId(correlation.getWhopMembershipId());
        subscription.setProviderCustomerId(correlation.getWhopUserId());
        subscription.setProviderUserId(correlation.getWhopUserId());
        subscription.setProviderProductId(correlation.getWhopProductId());
        subscription.setProviderPlanId(correlation.getWhopPlanId());
        subscription.setProviderStatus("active");
        subscription.setUser(user);
        subscription.setProduct(product);
        subscription.setStripeMode(StripeMode.DEFAULT);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingPeriod(billingPeriod(price));
        subscription.setCurrency(DEFAULT_CURRENCY);
        subscription.setPriceAtSubscription(priceAmount(price));
        subscription.setCurrentPeriodStart(now);
        subscription.setCancelAtPeriodEnd(false);
        return subscriptionRepository.save(subscription);
    }

    private boolean canGrantProduct(Product product) {
        if (product == null
            || !Boolean.TRUE.equals(product.getIsActive())
            || Boolean.TRUE.equals(product.getIsArchived())) {
            return false;
        }
        return product.getVisibility() == ProductVisibility.DIRECT_LINK_ONLY;
    }

    private ProductPrice priceForPlan(Product product, String planId) {
        if (product == null || product.getPrices() == null) {
            return null;
        }
        return product.getPrices().stream()
            .filter(price -> Boolean.TRUE.equals(price.getIsActive()))
            .filter(price -> planId == null || planId.equals(price.getWhopPlanId()))
            .findFirst()
            .orElse(null);
    }

    private BillingPeriod billingPeriod(ProductPrice price) {
        if (price == null || price.getBillingPeriod() == null) {
            return BillingPeriod.MONTHLY;
        }
        try {
            return BillingPeriod.valueOf(price.getBillingPeriod().name());
        } catch (IllegalArgumentException e) {
            return BillingPeriod.MONTHLY;
        }
    }

    private BigDecimal priceAmount(ProductPrice price) {
        if (price == null || price.getPriceCents() == null) {
            return null;
        }
        return BigDecimal.valueOf(price.getPriceCents()).movePointLeft(2);
    }
}
