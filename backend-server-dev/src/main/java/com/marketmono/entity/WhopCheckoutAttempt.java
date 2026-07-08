package com.marketmono.entity;

import com.marketmono.entity.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "whop_checkout_attempts", indexes = {
    @Index(name = "idx_whop_checkout_attempts_user_id", columnList = "user_id"),
    @Index(name = "idx_whop_checkout_attempts_status_expires", columnList = "status, expires_at"),
    @Index(name = "idx_whop_checkout_attempts_configuration", columnList = "whop_checkout_configuration_id"),
    @Index(name = "idx_whop_checkout_attempts_membership", columnList = "whop_membership_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class WhopCheckoutAttempt {

    public static final int DEFAULT_TTL_MINUTES = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_price_id", nullable = false)
    private ProductPrice productPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_cap_reservation_id")
    private SubscriberCapReservation capReservation;

    @Column(name = "whop_plan_id", nullable = false, length = 100)
    private String whopPlanId;

    @Column(name = "whop_checkout_configuration_id", length = 255)
    private String whopCheckoutConfigurationId;

    @Column(name = "whop_purchase_url", length = 500)
    private String whopPurchaseUrl;

    @Column(name = "buyer_identity_locked", nullable = false)
    @Builder.Default
    private boolean buyerIdentityLocked = false;

    @Column(name = "locked_buyer_email", length = 255)
    private String lockedBuyerEmail;

    @Column(name = "buyer_identity_lock_method", length = 50)
    private String buyerIdentityLockMethod;

    @Column(name = "whop_membership_id", length = 255)
    private String whopMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WhopCheckoutAttemptStatus status = WhopCheckoutAttemptStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static WhopCheckoutAttempt pending(
            User user,
            ProductPrice productPrice,
            String whopPlanId,
            SubscriberCapReservation capReservation) {
        return WhopCheckoutAttempt.builder()
            .user(user)
            .productPrice(productPrice)
            .whopPlanId(whopPlanId)
            .capReservation(capReservation)
            .status(WhopCheckoutAttemptStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(DEFAULT_TTL_MINUTES))
            .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public void markCreated(String configurationId, String purchaseUrl) {
        markCreated(configurationId, purchaseUrl, null, null);
    }

    public void markCreated(
            String configurationId,
            String purchaseUrl,
            String lockedBuyerEmail,
            String lockMethod) {
        this.status = WhopCheckoutAttemptStatus.CREATED;
        this.whopCheckoutConfigurationId = configurationId;
        this.whopPurchaseUrl = purchaseUrl;
        this.lockedBuyerEmail = lockedBuyerEmail;
        this.buyerIdentityLockMethod = lockMethod;
        this.buyerIdentityLocked = lockedBuyerEmail != null
            && !lockedBuyerEmail.isBlank()
            && lockMethod != null
            && !lockMethod.isBlank();
        this.failureReason = null;
    }

    public void markCompleted(String membershipId) {
        this.status = WhopCheckoutAttemptStatus.COMPLETED;
        this.whopMembershipId = membershipId;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = WhopCheckoutAttemptStatus.FAILED;
        this.failureReason = reason;
    }
}
