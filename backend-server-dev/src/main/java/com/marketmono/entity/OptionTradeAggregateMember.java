package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "option_trade_aggregate_members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_option_trade_aggregate_members_trade",
            columnNames = {"aggregate_id", "trade_id", "executed_at"}
        )
    },
    indexes = {
        @Index(name = "idx_option_trade_aggregate_members_aggregate_order", columnList = "aggregate_id, member_order"),
        @Index(name = "idx_option_trade_aggregate_members_trade_exec", columnList = "trade_id, executed_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionTradeAggregateMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "member_order", nullable = false)
    private Integer memberOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
