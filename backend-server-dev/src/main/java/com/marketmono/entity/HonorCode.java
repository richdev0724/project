package com.marketmono.entity;

import com.marketmono.entity.auth.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "honor_codes", indexes = {
    @Index(name = "idx_honor_codes_code", columnList = "code", unique = true),
    @Index(name = "idx_honor_codes_status", columnList = "status"),
    @Index(name = "idx_honor_codes_redeemed_by_user_id", columnList = "redeemed_by_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class HonorCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private HonorCodeStatus status = HonorCodeStatus.ACTIVE;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_by_user_id")
    private User redeemedByUser;

    @Column(name = "created_by_admin_id")
    private UUID createdByAdminId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Column(name = "validation_attempts", nullable = false)
    private Integer validationAttempts = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
