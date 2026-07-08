package com.marketmono.dto;

import com.marketmono.entity.Product;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Product DTO for storefront/pricing page display.
 * Simplified - includes prices list with Stripe price IDs.
 */
public record ProductStorefrontDto(
    UUID id,
    String name,
    String slug,
    String description,
    String shortDescription,
    
    // Pricing (from product_prices table)
    List<ProductPriceDto> prices,
    
    // Trial
    Integer trialDays,
    Boolean hasTrial,
    
    // Display
    Integer sortOrder,
    Boolean isAvailable,
    String visibility
) {
    /**
     * Create storefront DTO from entity.
     */
    public static ProductStorefrontDto from(Product product) {
        return new ProductStorefrontDto(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getShortDescription(),
            product.getPrices().stream()
                .map(ProductPriceDto::from)
                .sorted((a, b) -> a.sortOrder().compareTo(b.sortOrder()))
                .collect(Collectors.toList()),
            product.getTrialDays(),
            product.getTrialDays() > 0,
            product.getSortOrder(),
            product.isPubliclyAvailableForSubscription(),
            product.getVisibility().name()
        );
    }
}
