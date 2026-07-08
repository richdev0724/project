package com.marketmono.service;

import com.marketmono.dto.CheckoutCapabilityDto;
import com.marketmono.entity.CheckoutProviderOverride;
import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutCapabilityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WhopCheckoutConfigurationService whopCheckoutConfigurationService;

    @InjectMocks
    private CheckoutCapabilityService checkoutCapabilityService;

    @Test
    void shouldEnableDefaultUsersForPublicWhopCheckoutWhenConfigured() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, CheckoutProviderOverride.DEFAULT);
        Product product = whopOnlyProduct();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC))
            .thenReturn(List.of(product));
        when(whopCheckoutConfigurationService.isConfigured()).thenReturn(true);
        when(whopCheckoutConfigurationService.allowPromoCodes()).thenReturn(true);

        CheckoutCapabilityDto capability = checkoutCapabilityService.getCapability(userId);

        assertThat(capability.provider()).isEqualTo(CheckoutProviderOverride.WHOP);
        assertThat(capability.enabled()).isTrue();
        assertThat(capability.disabledReason()).isNull();
        assertThat(capability.prices()).singleElement()
            .satisfies(price -> {
                assertThat(price.priceId()).isEqualTo(product.getPrices().getFirst().getId());
                assertThat(price.whopConfigured()).isTrue();
                assertThat(price.checkoutConfigured()).isTrue();
            });
    }

    @Test
    void shouldEnableWhopOnlyPriceForWhopOverrideUser() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, CheckoutProviderOverride.WHOP);
        Product product = whopOnlyProduct();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC))
            .thenReturn(List.of(product));
        when(whopCheckoutConfigurationService.isConfigured()).thenReturn(true);
        when(whopCheckoutConfigurationService.allowPromoCodes()).thenReturn(true);

        CheckoutCapabilityDto capability = checkoutCapabilityService.getCapability(userId);

        assertThat(capability.provider()).isEqualTo(CheckoutProviderOverride.WHOP);
        assertThat(capability.enabled()).isTrue();
        assertThat(capability.disabledReason()).isNull();
        assertThat(capability.allowPromoCodes()).isTrue();
        assertThat(capability.prices()).singleElement()
            .satisfies(price -> {
                assertThat(price.priceId()).isEqualTo(product.getPrices().getFirst().getId());
                assertThat(price.stripeConfigured()).isFalse();
                assertThat(price.whopConfigured()).isTrue();
                assertThat(price.checkoutConfigured()).isTrue();
                assertThat(price.disabledReason()).isNull();
            });
    }

    @Test
    void shouldDisableDefaultCheckoutWhenWhopIsNotConfigured() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, CheckoutProviderOverride.DEFAULT);
        Product product = stripeAndWhopProduct();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC))
            .thenReturn(List.of(product));
        when(whopCheckoutConfigurationService.isConfigured()).thenReturn(false);

        CheckoutCapabilityDto capability = checkoutCapabilityService.getCapability(userId);

        assertThat(capability.provider()).isEqualTo(CheckoutProviderOverride.DEFAULT);
        assertThat(capability.enabled()).isFalse();
        assertThat(capability.disabledReason()).isEqualTo("checkout_disabled");
        assertThat(capability.allowPromoCodes()).isFalse();
        assertThat(capability.prices()).singleElement()
            .satisfies(price -> {
                assertThat(price.stripeConfigured()).isTrue();
                assertThat(price.whopConfigured()).isTrue();
                assertThat(price.checkoutConfigured()).isFalse();
                assertThat(price.disabledReason()).isEqualTo("checkout_disabled");
            });
    }

    @Test
    void shouldKeepExplicitStripeCapabilityAvailableForLegacyOverride() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, CheckoutProviderOverride.STRIPE);
        Product product = stripeAndWhopProduct();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC))
            .thenReturn(List.of(product));
        when(whopCheckoutConfigurationService.isConfigured()).thenReturn(false);

        CheckoutCapabilityDto capability = checkoutCapabilityService.getCapability(userId);

        assertThat(capability.provider()).isEqualTo(CheckoutProviderOverride.STRIPE);
        assertThat(capability.enabled()).isTrue();
        assertThat(capability.disabledReason()).isNull();
        assertThat(capability.prices()).singleElement()
            .satisfies(price -> {
                assertThat(price.stripeConfigured()).isTrue();
                assertThat(price.checkoutConfigured()).isTrue();
                assertThat(price.disabledReason()).isNull();
            });
    }

    @Test
    void shouldKeepDisabledCapabilityDisabled() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, CheckoutProviderOverride.DISABLED);
        Product product = stripeAndWhopProduct();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC))
            .thenReturn(List.of(product));
        when(whopCheckoutConfigurationService.isConfigured()).thenReturn(true);

        CheckoutCapabilityDto capability = checkoutCapabilityService.getCapability(userId);

        assertThat(capability.provider()).isEqualTo(CheckoutProviderOverride.DISABLED);
        assertThat(capability.enabled()).isFalse();
        assertThat(capability.disabledReason()).isEqualTo("checkout_disabled");
        assertThat(capability.prices()).singleElement()
            .satisfies(price -> {
                assertThat(price.checkoutConfigured()).isFalse();
                assertThat(price.disabledReason()).isEqualTo("checkout_disabled");
            });
    }

    @Test
    void shouldDisableExplicitWhopCapabilityWhenWhopIsNotConfigured() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, CheckoutProviderOverride.WHOP);
        Product product = stripeAndWhopProduct();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(ProductVisibility.PUBLIC))
            .thenReturn(List.of(product));
        when(whopCheckoutConfigurationService.isConfigured()).thenReturn(false);

        CheckoutCapabilityDto capability = checkoutCapabilityService.getCapability(userId);

        assertThat(capability.provider()).isEqualTo(CheckoutProviderOverride.WHOP);
        assertThat(capability.enabled()).isFalse();
        assertThat(capability.disabledReason()).isEqualTo("whop_not_configured");
        assertThat(capability.prices()).singleElement()
            .satisfies(price -> {
                assertThat(price.whopConfigured()).isTrue();
                assertThat(price.checkoutConfigured()).isFalse();
                assertThat(price.disabledReason()).isEqualTo("whop_not_configured");
            });
    }

    private static User user(UUID userId, CheckoutProviderOverride provider) {
        return User.builder()
            .id(userId)
            .email("buyer@example.com")
            .emailLowercase("buyer@example.com")
            .checkoutProviderOverride(provider)
            .build();
    }

    private static Product whopOnlyProduct() {
        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono")
            .slug("market-mono")
            .isActive(true)
            .isArchived(false)
            .visibility(ProductVisibility.PUBLIC)
            .build();
        product.addPrice(ProductPrice.builder()
            .id(UUID.randomUUID())
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .stripePriceId(null)
            .whopPlanId("plan_whop_monthly")
            .whopPurchaseUrl("https://whop.com/checkout/static-plan-url")
            .isActive(true)
            .sortOrder(0)
            .build());
        return product;
    }

    private static Product stripeAndWhopProduct() {
        Product product = Product.builder()
            .id(UUID.randomUUID())
            .name("Market Mono")
            .slug("market-mono")
            .isActive(true)
            .isArchived(false)
            .visibility(ProductVisibility.PUBLIC)
            .build();
        product.addPrice(ProductPrice.builder()
            .id(UUID.randomUUID())
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .stripePriceId("price_live_monthly")
            .whopPlanId("plan_whop_monthly")
            .whopPurchaseUrl("https://whop.com/checkout/static-plan-url")
            .isActive(true)
            .sortOrder(0)
            .build());
        return product;
    }
}
