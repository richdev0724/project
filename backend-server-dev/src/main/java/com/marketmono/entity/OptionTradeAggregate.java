package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "option_trade_aggregates", indexes = {
    @Index(name = "idx_option_trade_aggregates_ticker_last_executed", columnList = "ticker, last_executed_at"),
    @Index(name = "idx_option_trade_aggregates_option_chain_last_exec", columnList = "option_chain, last_executed_at"),
    @Index(name = "idx_option_trade_aggregates_last_executed", columnList = "last_executed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionTradeAggregate {

    @Id
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "grouping_version", nullable = false)
    @Builder.Default
    private Integer groupingVersion = 1;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "option_chain", nullable = false, length = 50)
    private String optionChain;

    @Column(name = "side", length = 50)
    private String side;

    @Column(name = "aggressor", length = 50)
    private String aggressor;

    @Column(name = "condition_signature", length = 100)
    private String conditionSignature;

    @Column(name = "is_sweep", nullable = false)
    @Builder.Default
    private Boolean isSweep = false;

    @Column(name = "is_floor_trade", nullable = false)
    @Builder.Default
    private Boolean isFloorTrade = false;

    @Column(name = "is_multileg", nullable = false)
    @Builder.Default
    private Boolean isMultileg = false;

    @Column(name = "first_executed_at", nullable = false)
    private Instant firstExecutedAt;

    @Column(name = "last_executed_at", nullable = false)
    private Instant lastExecutedAt;

    @Column(name = "representative_price", precision = 12, scale = 4)
    private BigDecimal representativePrice;

    @Column(name = "min_price", precision = 12, scale = 4)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 12, scale = 4)
    private BigDecimal maxPrice;

    @Column(name = "total_size", nullable = false)
    private Long totalSize;

    @Column(name = "total_premium", precision = 15, scale = 2)
    private BigDecimal totalPremium;

    @Column(name = "trade_count", nullable = false)
    private Integer tradeCount;

    @Column(name = "exchange_count", nullable = false)
    private Integer exchangeCount;

    @Column(name = "expiry")
    private LocalDate expiry;

    @Column(name = "option_type", length = 10)
    private String optionType;

    @Column(name = "strike", precision = 12, scale = 4)
    private BigDecimal strike;

    @Column(name = "underlying_price_first", precision = 12, scale = 4)
    private BigDecimal underlyingPriceFirst;

    @Column(name = "underlying_price_last", precision = 12, scale = 4)
    private BigDecimal underlyingPriceLast;

    @Column(name = "open_interest")
    private Integer openInterest;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (groupingVersion == null) {
            groupingVersion = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
