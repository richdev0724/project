package com.marketmono.entity;

import com.marketmono.entity.auth.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "option_trade_bookmarks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "trade_id", "source_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionTradeBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "trade_id", nullable = false)
    private String tradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    @Builder.Default
    private BookmarkSourceType sourceType = BookmarkSourceType.FA;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
