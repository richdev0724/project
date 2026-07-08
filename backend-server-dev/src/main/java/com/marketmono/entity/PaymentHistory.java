package com.marketmono.entity;

import com.marketmono.entity.auth.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment history entity for tracking subscription payments.
 * Records created from Stripe webhook events (invoice.paid, invoice.payment_failed).
 * Primary key is stripe_invoice_id since it's unique and immutable.
 */
@Entity
@Table(name = "payment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistory {
    
    @Id
    @Column(name = "stripe_invoice_id", length = 255, nullable = false)
    private String stripeInvoiceId;
    
    // ============================================
    // Relationships
    // ============================================
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;
    
    // ============================================
    // Stripe Payment Details
    // ============================================
    
    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_provider", nullable = false, length = 20)
    @Builder.Default
    private BillingProvider billingProvider = BillingProvider.STRIPE;

    @Column(name = "provider_payment_id", length = 255)
    private String providerPaymentId;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    @Column(name = "provider_customer_id", length = 255)
    private String providerCustomerId;

    @Column(name = "provider_status", length = 100)
    private String providerStatus;

    @Column(name = "provider_metadata", columnDefinition = "TEXT")
    private String providerMetadata;
    
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "payment_status", nullable = false)
    private PaymentStatus status;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    // ============================================
    // Billing Period
    // ============================================
    
    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;
    
    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;
    
    // ============================================
    // Payment Method Details (from webhook)
    // ============================================
    
    @Column(name = "payment_method_last4", length = 4)
    private String paymentMethodLast4;
    
    @Column(name = "payment_method_exp_month")
    private Integer paymentMethodExpMonth;
    
    @Column(name = "payment_method_exp_year")
    private Integer paymentMethodExpYear;
    
    // ============================================
    // Environment
    // ============================================
    
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "stripe_mode", columnDefinition = "stripe_mode", nullable = false)
    private StripeMode stripeMode;
    
    // ============================================
    // Audit Fields
    // ============================================
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ============================================
    // Helper Methods
    // ============================================
    
    /**
     * Check if payment was successful.
     */
    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }
    
    /**
     * Check if payment failed.
     */
    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }
    
    /**
     * Check if payment was refunded.
     */
    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }
    
    /**
     * Get billing period duration in days.
     */
    public long getBillingPeriodDays() {
        return java.time.Duration.between(periodStart, periodEnd).toDays();
    }
    
    /**
     * Check if payment method is expiring soon (within 30 days).
     */
    public boolean isPaymentMethodExpiringSoon() {
        if (paymentMethodExpMonth == null || paymentMethodExpYear == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiration = LocalDateTime.of(
            paymentMethodExpYear, 
            paymentMethodExpMonth, 
            1, 
            0, 
            0
        ).plusMonths(1).minusDays(1);
        
        return now.plusDays(30).isAfter(expiration);
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
