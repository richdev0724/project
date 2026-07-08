package com.marketmono.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Request DTO for creating a new product.
 * Simplified - prices are managed separately via ProductPriceRequest list.
 */
public record CreateProductRequest(
    @NotBlank(message = "Product name is required")
    @Size(max = 100, message = "Product name must be at most 100 characters")
    String name,
    
    @NotBlank(message = "Product slug is required")
    @Size(max = 50, message = "Product slug must be at most 50 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    String slug,
    
    @Size(max = 5000, message = "Description must be at most 5000 characters")
    String description,
    
    @Size(max = 255, message = "Short description must be at most 255 characters")
    String shortDescription,
    
    // Stripe (optional - can be set later)
    String stripeProductId,
    
    // Prices (list of billing periods with prices)
    @Valid
    @NotNull(message = "At least one price is required")
    @Size(min = 1, message = "At least one price is required")
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
     * Create with defaults for optional fields.
     */
    public CreateProductRequest {
        if (sortOrder == null) sortOrder = 0;
        if (isActive == null) isActive = true;
        if (visibility == null || visibility.isBlank()) visibility = "PUBLIC";
        if (trialDays == null) trialDays = 0;
    }
    
    /**
     * Request for a product price.
     */
    public record ProductPriceRequest(
        @NotBlank(message = "Billing period is required")
        String billingPeriod,
        
        @NotNull(message = "Price is required")
        @Min(value = 0, message = "Price must be non-negative")
        Integer priceCents,
        
        String stripePriceId,
        Boolean isActive,
        Integer sortOrder
    ) {
        public ProductPriceRequest {
            if (isActive == null) isActive = true;
            if (sortOrder == null) sortOrder = 0;
        }
    }
}
