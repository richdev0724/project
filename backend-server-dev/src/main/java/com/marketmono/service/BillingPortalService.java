package com.marketmono.service;

import com.marketmono.client.WhopMembershipClient;
import com.marketmono.client.WhopMembershipResponse;
import com.marketmono.dto.BillingPortalResponse;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.Subscription;
import com.marketmono.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingPortalService {

    public static final String STRIPE_PORTAL = "STRIPE_PORTAL";
    public static final String WHOP_MANAGE_URL = "WHOP_MANAGE_URL";
    public static final String WHOP_MEMBERSHIP_REFRESH = "WHOP_MEMBERSHIP_REFRESH";

    private final SubscriptionRepository subscriptionRepository;
    private final StripePortalService stripePortalService;
    private final WhopMembershipClient whopMembershipClient;

    @Transactional
    public BillingPortalResponse createPortalSession(UUID userId, String returnUrl) {
        Subscription subscription = subscriptionRepository.findActiveSubscriptionsByUserId(userId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new BillingPortalUnavailableException(
                "No active provider-backed subscription was found for user: " + userId
            ));

        if (subscription.getBillingProvider() == BillingProvider.STRIPE && hasText(subscription.getStripeSubscriptionId())) {
            return new BillingPortalResponse(
                stripePortalService.createPortalSession(userId, returnUrl),
                BillingProvider.STRIPE,
                STRIPE_PORTAL,
                "Redirect user to Stripe billing portal"
            );
        }

        if (subscription.getBillingProvider() == BillingProvider.WHOP) {
            return whopPortalResponse(subscription);
        }

        throw new BillingPortalUnavailableException(
            "No active provider-backed subscription was found for user: " + userId
        );
    }

    private BillingPortalResponse whopPortalResponse(Subscription subscription) {
        if (hasText(subscription.getProviderManageUrl())) {
            return new BillingPortalResponse(
                subscription.getProviderManageUrl(),
                BillingProvider.WHOP,
                WHOP_MANAGE_URL,
                "Redirect user to Whop billing portal"
            );
        }

        if (!hasText(subscription.getProviderSubscriptionId())) {
            throw new BillingPortalUnavailableException(
                "Whop subscription is missing membership ID for portal refresh"
            );
        }

        WhopMembershipResponse membership = whopMembershipClient.retrieveMembership(
            subscription.getProviderSubscriptionId()
        );
        if (membership == null || !hasText(membership.manageUrl())) {
            throw new BillingPortalUnavailableException(
                "Whop membership did not include a manage URL"
            );
        }

        subscription.setProviderManageUrl(membership.manageUrl());
        subscriptionRepository.save(subscription);
        return new BillingPortalResponse(
            membership.manageUrl(),
            BillingProvider.WHOP,
            WHOP_MEMBERSHIP_REFRESH,
            "Redirect user to Whop billing portal"
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class BillingPortalUnavailableException extends RuntimeException {
        public BillingPortalUnavailableException(String message) {
            super(message);
        }
    }
}
