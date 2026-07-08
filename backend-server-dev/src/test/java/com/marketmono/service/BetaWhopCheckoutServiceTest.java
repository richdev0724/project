package com.marketmono.service;

import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.auth.CustomerInviteStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BetaWhopCheckoutServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BetaWhopCheckoutService service;

    @Test
    void shouldResolveConfiguredDirectWhopBetaCheckoutPriceForInvites() {
        Product product = betaProduct();
        when(productRepository.findFirstByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(
            ProductVisibility.DIRECT_LINK_ONLY
        )).thenReturn(Optional.of(product));

        assertThat(service.betaCheckoutPriceForInvite())
            .extracting(ProductPrice::getWhopPlanId)
            .isEqualTo("plan_beta");
    }

    @Test
    void shouldResolvePromoCheckoutPriceOnlyForActiveInvitedUser() {
        UUID userId = UUID.randomUUID();
        User user = betaInvitedUser(userId);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findFirstByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(
            ProductVisibility.DIRECT_LINK_ONLY
        )).thenReturn(Optional.of(betaProduct()));

        assertThat(service.betaCheckoutPriceForPromo(userId, "BETAMM2026"))
            .extracting(ProductPrice::getWhopPlanId)
            .isEqualTo("plan_beta");
    }

    @Test
    void shouldRejectLegacyPromoRedirectForUninvitedUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("buyer@example.com")
            .emailLowercase("buyer@example.com")
            .enabled(true)
            .betaUser(false)
            .build();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.betaCheckoutPriceForPromo(userId, "BETAMM2026"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Beta checkout is not available");
    }

    @Test
    void shouldAllowDirectLinkGrantForInvitedWaitlistedUser() {
        User user = betaInvitedUser(UUID.randomUUID());
        user.setEnabled(false);

        assertThat(service.canGrantDirectLinkOnlyPurchase(user)).isTrue();
    }

    @Test
    void shouldRejectDirectLinkGrantForRevokedInvite() {
        User user = betaInvitedUser(UUID.randomUUID());
        user.setCustomerInviteStatus(CustomerInviteStatus.REVOKED);

        assertThat(service.canGrantDirectLinkOnlyPurchase(user)).isFalse();
    }

    @Test
    void shouldRejectDirectLinkGrantForExpiredInvite() {
        User user = betaInvitedUser(UUID.randomUUID());
        user.setCustomerInviteStatus(CustomerInviteStatus.EXPIRED);

        assertThat(service.canGrantDirectLinkOnlyPurchase(user)).isFalse();
    }

    @Test
    void shouldRejectLegacyPromoRedirectForRevokedInvite() {
        UUID userId = UUID.randomUUID();
        User user = betaInvitedUser(userId);
        user.setCustomerInviteStatus(CustomerInviteStatus.REVOKED);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.betaCheckoutPriceForPromo(userId, "BETAMM2026"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Beta checkout is not available");
    }

    @Test
    void shouldRejectLegacyPromoRedirectForExpiredInvite() {
        UUID userId = UUID.randomUUID();
        User user = betaInvitedUser(userId);
        user.setCustomerInviteStatus(CustomerInviteStatus.EXPIRED);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.betaCheckoutPriceForPromo(userId, "BETAMM2026"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Beta checkout is not available");
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
            .product(product)
            .billingPeriod(ProductPrice.BillingPeriod.MONTHLY)
            .priceCents(4000)
            .whopPlanId("plan_beta")
            .isActive(true)
            .sortOrder(1)
            .build());
        product.addPrice(ProductPrice.builder()
            .id(UUID.randomUUID())
            .product(product)
            .billingPeriod(ProductPrice.BillingPeriod.ANNUAL)
            .priceCents(40000)
            .whopPlanId("plan_beta_yearly")
            .isActive(true)
            .sortOrder(2)
            .build());
        return product;
    }

    private static User betaInvitedUser(UUID userId) {
        User user = User.builder()
            .id(userId)
            .email("buyer@example.com")
            .emailLowercase("buyer@example.com")
            .enabled(true)
            .betaUser(true)
            .build();
        user.setBetaAccess(UserBetaAccess.builder()
            .betaInvitedAt(LocalDateTime.now().minusDays(1))
            .betaPromoCode("BETAMM2026")
            .usedBetaPromo(false)
            .build());
        return user;
    }
}
