package com.marketmono.dto;

import com.marketmono.entity.Product;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Product summary DTO for list views.
 * Contains only essential fields for display.
 */
public record ProductSummaryDto(
    UUID id,
    String name,
    String slug,
    String shortDescription,
    List<ProductPriceDto> prices,
    Integer sortOrder,
    Boolean isActive,
    Boolean isArchived,
    String visibility,
    Integer trialDays
) {
    /**
     * Create summary DTO from entity.
     */
    public static ProductSummaryDto from(Product product) {
        return new ProductSummaryDto(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getShortDescription(),
            product.getPrices().stream()
                .map(ProductPriceDto::from)
                .sorted((a, b) -> a.sortOrder().compareTo(b.sortOrder()))
                .collect(Collectors.toList()),
            product.getSortOrder(),
            product.getIsActive(),
            product.getIsArchived(),
            product.getVisibility().name(),
            product.getTrialDays()
        );
    }
}
