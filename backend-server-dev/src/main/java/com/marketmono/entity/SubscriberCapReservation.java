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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriber_cap_reservations", indexes = {
    @Index(name = "idx_subscriber_cap_reservations_user_id", columnList = "user_id"),
    @Index(name = "idx_subscriber_cap_reservations_status_expires", columnList = "status, expires_at"),
    @Index(name = "idx_subscriber_cap_reservations_stripe_session", columnList = "stripe_checkout_session_id"),
    @Index(name = "idx_subscriber_cap_reservations_stripe_subscription", columnList = "stripe_subscription_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class SubscriberCapReservation {

    public static final int DEFAULT_TTL_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "price_id", nullable = false, length = 255)
    private String priceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_provider", nullable = false, length = 20)
    @Builder.Default
    private BillingProvider checkoutProvider = BillingProvider.STRIPE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "stripe_mode", columnDefinition = "stripe_mode", nullable = false)
    private StripeMode stripeMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriberCapReservationStatus status = SubscriberCapReservationStatus.PENDING;

    @Column(name = "stripe_checkout_session_id", length = 255)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    @Column(name = "whop_checkout_configuration_id", length = 255)
    private String whopCheckoutConfigurationId;

    @Column(name = "whop_membership_id", length = 255)
    private String whopMembershipId;

    @Column(name = "policy_limit", nullable = false)
    private Integer policyLimit;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static SubscriberCapReservation pending(User user, String priceId, StripeMode stripeMode, int policyLimit) {
        return SubscriberCapReservation.builder()
            .user(user)
            .priceId(priceId)
            .checkoutProvider(BillingProvider.STRIPE)
            .stripeMode(stripeMode)
            .policyLimit(policyLimit)
            .status(SubscriberCapReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(DEFAULT_TTL_MINUTES))
            .build();
    }

    public static SubscriberCapReservation pendingWhop(User user, String priceId, int policyLimit) {
        return SubscriberCapReservation.builder()
            .user(user)
            .priceId(priceId)
            .checkoutProvider(BillingProvider.WHOP)
            .stripeMode(StripeMode.PROD)
            .policyLimit(policyLimit)
            .status(SubscriberCapReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(DEFAULT_TTL_MINUTES))
            .build();
    }

    public boolean isPending() {
        return status == SubscriberCapReservationStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public void markStripeSessionCreated(String stripeCheckoutSessionId) {
        this.stripeCheckoutSessionId = stripeCheckoutSessionId;
    }

    public void markWhopCheckoutCreated(String whopCheckoutConfigurationId) {
        this.whopCheckoutConfigurationId = whopCheckoutConfigurationId;
    }

    public void consume(String stripeSubscriptionId) {
        this.status = SubscriberCapReservationStatus.CONSUMED;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.consumedAt = LocalDateTime.now();
    }

    public void consumeWhopMembership(String whopMembershipId) {
        this.status = SubscriberCapReservationStatus.CONSUMED;
        this.whopMembershipId = whopMembershipId;
        this.consumedAt = LocalDateTime.now();
    }

    public void release() {
        this.status = SubscriberCapReservationStatus.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }
}
