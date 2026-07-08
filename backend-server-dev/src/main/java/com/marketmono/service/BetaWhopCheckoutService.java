package com.marketmono.service;

import com.marketmono.entity.Product;
import com.marketmono.entity.ProductPrice;
import com.marketmono.entity.ProductVisibility;
import com.marketmono.entity.auth.CustomerInviteStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserBetaAccess;
import com.marketmono.repository.ProductRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BetaWhopCheckoutService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ProductPrice betaCheckoutPriceForInvite() {
        return betaCheckoutPrice()
            .orElseThrow(() -> new IllegalStateException("Beta checkout is not configured"));
    }

    @Transactional(readOnly = true)
    public ProductPrice betaCheckoutPriceForPromo(UUID userId, String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            throw new IllegalArgumentException("Beta promo code is required");
        }
        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!isActiveAccount(user) || !hasStoredBetaInvite(user, promoCode)) {
            throw new IllegalStateException("Beta checkout is not available for this account");
        }
        return betaCheckoutPriceForInvite();
    }

    public boolean isDirectLinkOnlyProduct(Product product) {
        return product != null && product.getVisibility() == ProductVisibility.DIRECT_LINK_ONLY;
    }

    public boolean canGrantDirectLinkOnlyPurchase(User user) {
        return isUsableAccountRecord(user) && hasStoredBetaInvite(user, null);
    }

    private Optional<ProductPrice> betaCheckoutPrice() {
        return productRepository
            .findFirstByVisibilityAndIsActiveTrueAndIsArchivedFalseOrderBySortOrderAsc(
                ProductVisibility.DIRECT_LINK_ONLY
            )
            .flatMap(this::checkoutPrice);
    }

    private Optional<ProductPrice> checkoutPrice(Product product) {
        if (product.getPrices() == null) {
            return Optional.empty();
        }
        return product.getPrices().stream()
            .filter(price -> Boolean.TRUE.equals(price.getIsActive()))
            .filter(price -> hasText(price.getWhopPlanId()))
            .min(Comparator.comparing(price -> price.getSortOrder() == null ? 0 : price.getSortOrder()))
            .map(price -> {
                price.setProduct(product);
                return price;
            });
    }

    private boolean hasStoredBetaInvite(User user, String promoCode) {
        UserBetaAccess betaAccess = user.getBetaAccess();
        if (!Boolean.TRUE.equals(user.getBetaUser()) || betaAccess == null) {
            return false;
        }
        if (user.getCustomerInviteStatus() == CustomerInviteStatus.REVOKED
            || user.getCustomerInviteStatus() == CustomerInviteStatus.EXPIRED) {
            return false;
        }
        boolean invited = betaAccess.getBetaInvitedAt() != null || betaAccess.getBetaInviteLastSentAt() != null;
        if (!invited) {
            return false;
        }
        if (!hasText(promoCode) || !hasText(betaAccess.getBetaPromoCode())) {
            return true;
        }
        return betaAccess.getBetaPromoCode().equalsIgnoreCase(promoCode.trim());
    }

    private boolean isActiveAccount(User user) {
        return isUsableAccountRecord(user)
            && Boolean.TRUE.equals(user.getEnabled())
            && !user.isAccountLocked();
    }

    private boolean isUsableAccountRecord(User user) {
        return user != null
            && !user.isDeleted()
            && !user.isBanned();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
