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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "whop_membership_correlations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_whop_membership_correlations_membership",
            columnNames = "whop_membership_id"
        )
    },
    indexes = {
        @Index(name = "idx_whop_membership_correlations_status", columnList = "status"),
        @Index(name = "idx_whop_membership_correlations_email", columnList = "buyer_email"),
        @Index(name = "idx_whop_membership_correlations_product_plan", columnList = "whop_product_id, whop_plan_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class WhopMembershipCorrelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "whop_membership_id", nullable = false, length = 255)
    private String whopMembershipId;

    @Column(name = "whop_user_id", length = 255)
    private String whopUserId;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(name = "whop_product_id", length = 255)
    private String whopProductId;

    @Column(name = "whop_plan_id", length = 255)
    private String whopPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private WhopMembershipCorrelationStatus status;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "last_webhook_event_id", length = 255)
    private String lastWebhookEventId;

    @Column(name = "last_payload_sha256", length = 64)
    private String lastPayloadSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
