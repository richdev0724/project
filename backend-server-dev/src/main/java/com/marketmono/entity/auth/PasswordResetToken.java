package com.marketmono.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Password reset token for forgot-password flow.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, unique = true)
    private String token;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Check if the token is expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if the token has been used.
     */
    public boolean isUsed() {
        return usedAt != null;
    }
    
    /**
     * Check if the token is valid (not expired and not used).
     */
    public boolean isValid() {
        return !isExpired() && !isUsed();
    }
    
    /**
     * Mark the token as used.
     */
    public void markUsed() {
        this.usedAt = LocalDateTime.now();
    }
    
    /**
     * Create a new password reset token with 1-hour expiration.
     */
    public static PasswordResetToken create(User user) {
        return PasswordResetToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plusHours(1))
            .build();
    }
}
