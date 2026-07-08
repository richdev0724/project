package com.marketmono.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Email verification token for user email verification flow.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class EmailVerificationToken {
    
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
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
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
        return verifiedAt != null;
    }
    
    /**
     * Check if the token is valid (not expired and not used).
     */
    public boolean isValid() {
        return !isExpired() && !isUsed();
    }
    
    /**
     * Mark the token as verified.
     */
    public void markVerified() {
        this.verifiedAt = LocalDateTime.now();
    }
    
    /**
     * Create a new verification token with 24-hour expiration.
     */
    public static EmailVerificationToken create(User user) {
        return EmailVerificationToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();
    }
}
