package com.marketmono.service;

import com.marketmono.dto.SubscriptionFeatureAccessDto;
import com.marketmono.dto.UserSubscriptionDto;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionFeatureServiceTest {

    private final SubscriptionFeatureService subscriptionFeatureService = new SubscriptionFeatureService();

    @Test
    void shouldReturnNoFeatureAccessForUserWithoutSubscription() {
        SubscriptionFeatureAccessDto featureAccess =
            subscriptionFeatureService.getFeatureAccess(UserSubscriptionDto.noSubscription());

        assertThat(featureAccess).isEqualTo(SubscriptionFeatureAccessDto.none());
    }

    @Test
    void shouldReturnPremiumFeatureAccessForKnownPaidProduct() {
        UserSubscriptionDto subscription = subscription("market-mono-monthly", true);

        SubscriptionFeatureAccessDto featureAccess =
            subscriptionFeatureService.getFeatureAccess(subscription);

        assertThat(featureAccess).isEqualTo(SubscriptionFeatureAccessDto.premium());
    }

    @Test
    void shouldDefaultUnknownPaidProductToPremiumFeatureAccess() {
        UserSubscriptionDto subscription = subscription("custom-paid-tier", true);

        SubscriptionFeatureAccessDto featureAccess =
            subscriptionFeatureService.getFeatureAccess(subscription);

        assertThat(featureAccess).isEqualTo(SubscriptionFeatureAccessDto.premium());
    }

    private UserSubscriptionDto subscription(String productSlug, boolean hasAccess) {
        return new UserSubscriptionDto(
            UUID.randomUUID(),
            SubscriptionStatus.ACTIVE,
            BillingPeriod.MONTHLY,
            null,
            false,
            null,
            BillingProvider.STRIPE,
            null,
            UUID.randomUUID(),
            "Feature Test Product",
            productSlug,
            null,
            null,
            true,
            hasAccess,
            false,
            false,
            30L,
            null,
            null
        );
    }
}
