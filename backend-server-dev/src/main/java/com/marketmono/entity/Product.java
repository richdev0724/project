package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Product entity representing a subscription tier.
 * Simplified schema - pricing moved to ProductPrice table, feature flags removed.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    // ============================================
    // Core Fields
    // ============================================
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, unique = true, length = 50)
    private String slug;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "short_description", length = 255)
    private String shortDescription;
    
    // ============================================
    // Stripe Integration
    // ============================================
    
    @Column(name = "stripe_product_id", length = 100)
    private String stripeProductId;

    @Column(name = "whop_product_id", length = 100)
    private String whopProductId;

    @Column(name = "whop_checkout_url", length = 500)
    private String whopCheckoutUrl;
    
    // ============================================
    // Pricing Relationship
    // ============================================
    
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ProductPrice> prices = new ArrayList<>();
    
    // ============================================
    // Display & Status
    // ============================================
    
    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Builder.Default
    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 30)
    private ProductVisibility visibility = ProductVisibility.PUBLIC;
    
    // ============================================
    // Trial
    // ============================================
    
    @Builder.Default
    @Column(name = "trial_days", nullable = false)
    private Integer trialDays = 0;
    
    // ============================================
    // Audit Timestamps
    // ============================================
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ============================================
    // Helper Methods
    // ============================================
    
    /**
     * Check if product is available for new subscriptions.
     */
    public boolean isAvailableForSubscription() {
        return isActive && !isArchived;
    }

    public boolean isPubliclyAvailableForSubscription() {
        return isAvailableForSubscription() && visibility == ProductVisibility.PUBLIC;
    }
    
    /**
     * Add a price to this product.
     */
    public void addPrice(ProductPrice price) {
        prices.add(price);
        price.setProduct(this);
    }
    
    /**
     * Remove a price from this product.
     */
    public void removePrice(ProductPrice price) {
        prices.remove(price);
        price.setProduct(null);
    }
}
