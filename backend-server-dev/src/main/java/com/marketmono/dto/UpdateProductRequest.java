package com.marketmono.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for updating an existing product.
 * All fields are optional - only provided fields will be updated.
 * Simplified - prices are managed separately.
 */
public record UpdateProductRequest(
    @NotNull(message = "Product ID is required")
    UUID id,
    
    @Size(max = 100, message = "Product name must be at most 100 characters")
    String name,
    
    @Size(max = 50, message = "Product slug must be at most 50 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    String slug,
    
    @Size(max = 5000, message = "Description must be at most 5000 characters")
    String description,
    
    @Size(max = 255, message = "Short description must be at most 255 characters")
    String shortDescription,
    
    // Stripe
    String stripeProductId,
    
    // Prices (optional - if provided, replaces all existing prices)
    @Valid
    List<ProductPriceRequest> prices,
    
    // Display
    Integer sortOrder,
    Boolean isActive,
    String visibility,
    
    // Trial
    @Min(value = 0, message = "Trial days must be non-negative")
    Integer trialDays
) {
    
    /**
     * Request for a product price.
     */
    public record ProductPriceRequest(
        UUID id, // null for new prices, set for updates
        
        @NotBlank(message = "Billing period is required")
        String billingPeriod,
        
        @NotNull(message = "Price is required")
        @Min(value = 0, message = "Price must be non-negative")
        Integer priceCents,
        
        String stripePriceId,
        Boolean isActive,
        Integer sortOrder
    ) {}
}
