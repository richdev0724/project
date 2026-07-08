package com.marketmono.entity;

import com.marketmono.entity.auth.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks a referred signup from code capture through qualification.
 */
@Entity
@Table(name = "referrals", indexes = {
    @Index(name = "idx_referrals_referrer_user_id", columnList = "referrer_user_id"),
    @Index(name = "idx_referrals_status", columnList = "status"),
    @Index(name = "idx_referrals_referral_code", columnList = "referral_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_user_id", nullable = false)
    private User referrer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_user_id", nullable = false, unique = true)
    private User referredUser;

    @Column(name = "referral_code", nullable = false, length = 8)
    private String referralCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qualifying_subscription_id")
    private Subscription qualifyingSubscription;

    @Column(name = "qualifying_stripe_invoice_id", length = 255)
    private String qualifyingStripeInvoiceId;

    @Column(name = "qualified_at")
    private LocalDateTime qualifiedAt;

    @OneToOne(mappedBy = "referral", fetch = FetchType.LAZY)
    private ReferralReward reward;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
