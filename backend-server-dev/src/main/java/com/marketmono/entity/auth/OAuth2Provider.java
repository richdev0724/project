package com.marketmono.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Linked external OAuth provider identity for an application user.
 */
@Entity
@Table(name = "oauth2_providers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    void prePersist() {
        if (linkedAt == null) {
            linkedAt = LocalDateTime.now();
        }
    }

    public static OAuth2Provider link(User user, String provider, String providerUserId) {
        return OAuth2Provider.builder()
            .user(user)
            .provider(provider)
            .providerUserId(providerUserId)
            .linkedAt(LocalDateTime.now())
            .build();
    }
}
