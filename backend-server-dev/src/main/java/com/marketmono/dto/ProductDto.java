package com.marketmono.dto;

import com.marketmono.entity.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full product DTO with all fields.
 * Used for detailed views and admin operations.
 * Simplified - matches new Product entity structure.
 */
public record ProductDto(
    UUID id,
    String name,
    String slug,
    String description,
    String shortDescription,
    
    // Stripe Integration
    String stripeProductId,
    
    // Pricing (from product_prices table)
    List<ProductPriceDto> prices,
    
    // Display & Status
    Integer sortOrder,
    Boolean isActive,
    Boolean isArchived,
    String visibility,
    
    // Trial
    Integer trialDays,
    
    // Audit
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    
    // Computed values (for frontend display)
    Boolean availableForSubscription
) {
    /**
     * Create DTO from entity.
     */
    public static ProductDto from(Product product) {
        return new ProductDto(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getShortDescription(),
            product.getStripeProductId(),
            product.getPrices().stream()
                .map(ProductPriceDto::from)
                .sorted((a, b) -> a.sortOrder().compareTo(b.sortOrder()))
                .collect(Collectors.toList()),
            product.getSortOrder(),
            product.getIsActive(),
            product.getIsArchived(),
            product.getVisibility().name(),
            product.getTrialDays(),
            product.getCreatedAt(),
            product.getUpdatedAt(),
            product.isAvailableForSubscription()
        );
    }
}
