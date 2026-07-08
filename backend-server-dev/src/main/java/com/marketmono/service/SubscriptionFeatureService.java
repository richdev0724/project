package com.marketmono.service;

import com.marketmono.dto.SubscriptionFeatureAccessDto;
import com.marketmono.dto.UserSubscriptionDto;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resolves feature-level access for the current user's subscription.
 *
 * Historical WS5 planning expected per-product feature flags, but the current
 * live schema was simplified to prices + trial metadata only. This service
 * reintroduces a single place where feature entitlements can be modeled without
 * changing today's premium behavior: all current paid products resolve to the
 * same premium feature set, while unsubscribed users resolve to no access.
 */
@Service
public class SubscriptionFeatureService {

    private static final SubscriptionFeatureAccessDto NO_FEATURES = SubscriptionFeatureAccessDto.none();
    private static final SubscriptionFeatureAccessDto PREMIUM_FEATURES = SubscriptionFeatureAccessDto.premium();

    private static final Map<String, SubscriptionFeatureAccessDto> PRODUCT_FEATURE_MATRIX = Map.of(
        "market-mono-monthly", PREMIUM_FEATURES,
        "ws5-admin-price-check", PREMIUM_FEATURES
    );

    public SubscriptionFeatureAccessDto getFeatureAccess(UserSubscriptionDto subscription) {
        if (subscription == null || !Boolean.TRUE.equals(subscription.hasAccess())) {
            return NO_FEATURES;
        }

        if (subscription.productSlug() == null || subscription.productSlug().isBlank()) {
            return PREMIUM_FEATURES;
        }

        return PRODUCT_FEATURE_MATRIX.getOrDefault(subscription.productSlug(), PREMIUM_FEATURES);
    }
}