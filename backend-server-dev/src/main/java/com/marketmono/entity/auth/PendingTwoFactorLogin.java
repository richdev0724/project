package com.marketmono.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a pending 2FA verification request.
 * Created when a user with 2FA enabled logs in successfully with password
 * but hasn't completed 2FA verification yet.
 */
@Entity
@Table(name = "pending_2fa_logins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PendingTwoFactorLogin {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /**
     * Unique token for this 2FA verification request.
     */
    @Column(nullable = false, unique = true)
    private String token;
    
    /**
     * When this request expires (typically 5 minutes after creation).
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    /**
     * User agent from the original login request.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    /**
     * IP address from the original login request.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Whether this request has been completed.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean completed = false;
    
    private static final int EXPIRATION_MINUTES = 5;
    
    /**
     * Create a new pending 2FA login request.
     */
    public static PendingTwoFactorLogin create(User user, String userAgent, String ipAddress) {
        return PendingTwoFactorLogin.builder()
            .user(user)
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES))
            .userAgent(userAgent)
            .ipAddress(ipAddress)
            .build();
    }
    
    /**
     * Check if this request is still valid.
     */
    public boolean isValid() {
        return !completed && LocalDateTime.now().isBefore(expiresAt);
    }
    
    /**
     * Mark this request as completed.
     */
    public void markCompleted() {
        this.completed = true;
    }
}
