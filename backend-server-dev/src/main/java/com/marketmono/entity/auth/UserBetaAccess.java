package com.marketmono.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_beta_access")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBetaAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "beta_invited_at")
    private LocalDateTime betaInvitedAt;

    @Column(name = "beta_signed_up_at")
    private LocalDateTime betaSignedUpAt;

    @Column(name = "beta_invite_last_sent_at")
    private LocalDateTime betaInviteLastSentAt;

    @Column(name = "beta_invited_by")
    private UUID betaInvitedBy;

    @Column(name = "beta_promo_code", length = 40)
    private String betaPromoCode;

    @Builder.Default
    @Column(name = "used_beta_promo", nullable = false)
    private Boolean usedBetaPromo = false;

    @Column(name = "beta_promo_applied_at")
    private LocalDateTime betaPromoAppliedAt;
}
