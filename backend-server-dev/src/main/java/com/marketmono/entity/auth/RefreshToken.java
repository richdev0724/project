package com.marketmono.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh token for JWT refresh flow.
 * Includes device tracking for session auditing.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, unique = true, length = 500)
    private String token;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Builder.Default
    @Column(nullable = false)
    private Boolean revoked = false;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    /**
     * Check if the token is expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if the token is valid (not expired and not revoked).
     */
    public boolean isValid() {
        return !isExpired() && !revoked;
    }
    
    /**
     * Revoke this refresh token.
     */
    public void revoke() {
        this.revoked = true;
    }
    
    /**
     * Create a new refresh token with 7-day expiration.
     */
    public static RefreshToken create(User user, String userAgent, String ipAddress) {
        return RefreshToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .userAgent(userAgent)
            .ipAddress(ipAddress)
            .build();
    }
}
