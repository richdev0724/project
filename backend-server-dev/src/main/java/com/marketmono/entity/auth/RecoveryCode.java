package com.marketmono.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing a 2FA recovery code (backup code).
 * Recovery codes are single-use backup codes for when TOTP is unavailable.
 */
@Entity
@Table(name = "two_factor_backup_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class RecoveryCode {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    /**
     * Hashed recovery code (using bcrypt).
     * We store hashes, not plain codes, for security.
     */
    @Column(name = "code_hash", nullable = false)
    private String codeHash;
    
    /**
     * Whether this code has been used.
     * Recovery codes are single-use only.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean used = false;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Mark this recovery code as used.
     */
    public void markAsUsed() {
        this.used = true;
    }
    
    /**
     * Check if this recovery code has been used.
     */
    public boolean isUsed() {
        return Boolean.TRUE.equals(this.used);
    }
}
