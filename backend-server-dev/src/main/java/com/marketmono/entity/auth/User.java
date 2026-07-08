package com.marketmono.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User entity for authentication and authorization.
 * Supports email/password authentication, OAuth2, and 2FA.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String email;
    
    @Column(name = "email_lowercase", nullable = false)
    private String emailLowercase;
    
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    @Column(name = "password_hash")
    private String passwordHash;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 40)
    private UserRegistrationStatus registrationStatus = UserRegistrationStatus.REGISTERED;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;
    
    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;
    
    @Builder.Default
    @Column(name = "two_factor_enabled", nullable = false)
    private Boolean twoFactorEnabled = false;
    
    @Column(name = "two_factor_secret")
    private String twoFactorSecret;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "waitlist_joined_at")
    private LocalDateTime waitlistJoinedAt;

    @Column(name = "waitlist_source", length = 80)
    private String waitlistSource;

    @Column(name = "waitlist_confirmation_email_sent_at")
    private LocalDateTime waitlistConfirmationEmailSentAt;

    @Builder.Default
    @Column(name = "requires_password_change", nullable = false)
    private Boolean requiresPasswordChange = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "temp_password_expires_at")
    private LocalDateTime tempPasswordExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_invite_status", length = 40)
    private CustomerInviteStatus customerInviteStatus;

    @Column(name = "customer_invited_at")
    private LocalDateTime customerInvitedAt;

    @Column(name = "customer_invite_last_sent_at")
    private LocalDateTime customerInviteLastSentAt;

    @Column(name = "customer_invite_revoked_at")
    private LocalDateTime customerInviteRevokedAt;

    @Column(name = "customer_invited_by")
    private UUID customerInvitedBy;
    
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    @Column(name = "banned_at")
    private LocalDateTime bannedAt;

    @Column(name = "banned_email_lowercase", length = 255)
    private String bannedEmailLowercase;

    @Column(name = "banned_reason", length = 500)
    private String bannedReason;

    @Column(name = "banned_by_admin_id")
    private UUID bannedByAdminId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    // ============================================
    // Stripe Integration Fields
    // ============================================
    
    @Enumerated(EnumType.STRING)
    @Column(name = "stripe_mode", nullable = false, length = 10)
    @Builder.Default
    private com.marketmono.entity.StripeMode stripeMode = com.marketmono.entity.StripeMode.DEFAULT;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_provider_override", nullable = false, length = 20)
    @Builder.Default
    private com.marketmono.entity.CheckoutProviderOverride checkoutProviderOverride =
        com.marketmono.entity.CheckoutProviderOverride.DEFAULT;
    
    @Column(name = "stripe_customer_id_test", length = 100)
    private String stripeCustomerIdTest;
    
    @Column(name = "stripe_customer_id_prod", length = 100)
    private String stripeCustomerIdProd;

    @Column(name = "referral_code", unique = true, length = 8)
    private String referralCode;

    @Builder.Default
    @Column(name = "is_partner_user", nullable = false)
    private Boolean partnerUser = false;

    @Builder.Default
    @Column(name = "is_beta_user", nullable = false)
    private Boolean betaUser = false;

    @Builder.Default
    @Column(name = "honor_user", nullable = false)
    private Boolean honorUser = false;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserBetaAccess betaAccess;
    
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UserRole> roles = new HashSet<>();
    
    /**
     * Set email and auto-populate lowercase version for case-insensitive queries.
     */
    public void setEmail(String email) {
        this.email = email;
        this.emailLowercase = email != null ? email.toLowerCase() : null;
    }

    public void setBetaAccess(UserBetaAccess betaAccess) {
        this.betaAccess = betaAccess;
        if (betaAccess != null) {
            betaAccess.setUser(this);
        }
    }

    public UserBetaAccess ensureBetaAccess() {
        if (betaAccess == null) {
            setBetaAccess(UserBetaAccess.builder().build());
        }
        return betaAccess;
    }
    
    /**
     * Check if the account is currently locked.
     */
    public boolean isAccountLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }
    
    /**
     * Check if the account is soft-deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isBanned() {
        return bannedAt != null;
    }
    
    /**
     * Add a role to this user.
     */
    public void addRole(UserRoleType roleType) {
        UserRole role = UserRole.builder()
            .user(this)
            .role(roleType)
            .build();
        roles.add(role);
    }
    
    /**
     * Remove a role from this user.
     */
    public void removeRole(UserRoleType roleType) {
        roles.removeIf(r -> r.getRole() == roleType);
    }
    
    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(UserRoleType roleType) {
        return roles.stream().anyMatch(r -> r.getRole() == roleType);
    }
    
    /**
     * Increment failed login attempts and lock if threshold reached.
     */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }
    
    /**
     * Reset failed login attempts on successful login.
     */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = LocalDateTime.now();
    }
}
