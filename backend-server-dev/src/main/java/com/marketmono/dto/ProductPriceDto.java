package com.marketmono.dto;

import com.marketmono.entity.ProductPrice;

/**
 * DTO for product pricing information.
 * Exposed to frontend for checkout integration.
 */
public record ProductPriceDto(
    java.util.UUID id,
    String billingPeriod,
    Integer priceCents,
    String stripePriceId,
    Boolean whopConfigured,
    Boolean isActive,
    Integer sortOrder
) {
    
    public static ProductPriceDto from(ProductPrice price) {
        if (price == null) return null;
        
        return new ProductPriceDto(
            price.getId(),
            price.getBillingPeriod().name(),
            price.getPriceCents(),
            price.getStripePriceId(),
            hasText(price.getWhopPlanId()),
            price.getIsActive(),
            price.getSortOrder()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    
    /**
     * Get price in dollars.
     */
    public double getPriceInDollars() {
        return priceCents / 100.0;
    }
}
