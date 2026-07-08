package com.marketmono.service;

import com.marketmono.dto.CheckoutCapabilityDto;
import com.marketmono.dto.CheckoutPriceCapabilityDto;
import com.marketmono.entity.CheckoutProviderOverride;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutCapabilityService {

    private static final String CHECKOUT_DISABLED = "checkout_disabled";
    private static final String WHOP_NOT_CONFIGURED = "whop_not_configured";
    private static final String STRIPE_PRICE_NOT_CONFIGURED = "stripe_price_not_configured";
    private static final String WHOP_PLAN_NOT_CONFIGURED = "whop_plan_not_configured";

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final WhopCheckoutConfigurationService whopCheckoutConfigurationService;

    @Transactional(readOnly = true)
    public CheckoutCapabilityDto getCapability(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        CheckoutProviderOverride provider = provider(user);
        boolean whopConfigured = whopCheckoutConfigurationService.isConfigured();
        CheckoutProviderOverride effectiveProvider = effectiveProvider(provider, whopConfigured);
        boolean enabled = switch (provider) {
            case WHOP -> whopConfigured;
            case STRIPE -> true;
            case DEFAULT -> whopConfigured;
            case DISABLED -> false;
        };
        String disabledReason = disabledReason(provider, whopConfigured);

        List<CheckoutPriceCapabilityDto> prices = productRepository
            .findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC)
            .stream()
            .flatMap(product -> product.getPrices().stream()
                .filter(price -> Boolean.TRUE.equals(price.getIsActive()))
                .map(price -> priceCapability(product, price, effectiveProvider, whopConfigured)))
            .toList();

        return new CheckoutCapabilityDto(
            effectiveProvider,
            enabled,
            disabledReason,
            effectiveProvider == CheckoutProviderOverride.WHOP && whopCheckoutConfigurationService.allowPromoCodes(),
            prices
        );
    }

    private CheckoutPriceCapabilityDto priceCapability(
            Product product,
            ProductPrice price,
            CheckoutProviderOverride provider,
            boolean whopConfigured) {
        boolean stripeConfigured = hasText(price.getStripePriceId());
        boolean whopPlanConfigured = hasText(price.getWhopPlanId());
        boolean checkoutConfigured = switch (provider) {
            case STRIPE -> stripeConfigured;
            case WHOP -> whopConfigured && whopPlanConfigured;
            case DEFAULT, DISABLED -> false;
        };
        return new CheckoutPriceCapabilityDto(
            product.getId(),
            price.getId(),
            price.getBillingPeriod().name(),
            stripeConfigured,
            whopPlanConfigured,
            checkoutConfigured,
            priceDisabledReason(provider, whopConfigured, stripeConfigured, whopPlanConfigured)
        );
    }

    private CheckoutProviderOverride provider(User user) {
        CheckoutProviderOverride provider = user.getCheckoutProviderOverride();
        return provider == null ? CheckoutProviderOverride.DEFAULT : provider;
    }

    private CheckoutProviderOverride effectiveProvider(CheckoutProviderOverride provider, boolean whopConfigured) {
        if (provider == CheckoutProviderOverride.DEFAULT && whopConfigured) {
            return CheckoutProviderOverride.WHOP;
        }
        return provider;
    }

    private String disabledReason(CheckoutProviderOverride provider, boolean whopConfigured) {
        return switch (provider) {
            case WHOP -> whopConfigured ? null : WHOP_NOT_CONFIGURED;
            case STRIPE -> null;
            case DEFAULT -> whopConfigured ? null : CHECKOUT_DISABLED;
            case DISABLED -> CHECKOUT_DISABLED;
        };
    }

    private String priceDisabledReason(
            CheckoutProviderOverride provider,
            boolean whopConfigured,
            boolean stripeConfigured,
            boolean whopPlanConfigured) {
        return switch (provider) {
            case STRIPE -> stripeConfigured ? null : STRIPE_PRICE_NOT_CONFIGURED;
            case WHOP -> {
                if (!whopConfigured) {
                    yield WHOP_NOT_CONFIGURED;
                }
                yield whopPlanConfigured ? null : WHOP_PLAN_NOT_CONFIGURED;
            }
            case DEFAULT, DISABLED -> CHECKOUT_DISABLED;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
