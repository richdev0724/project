package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_prices")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ProductPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 20)
    private BillingPeriod billingPeriod;

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents;

    @Column(name = "stripe_price_id", length = 100)
    private String stripePriceId;

    @Column(name = "whop_plan_id", length = 100)
    private String whopPlanId;

    @Column(name = "whop_purchase_url", length = 500)
    private String whopPurchaseUrl;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BillingPeriod {
        MONTHLY,
        QUARTERLY,
        SEMI_ANNUAL,
        ANNUAL,
        LIFETIME
    }
}
