package com.marketmono.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "option_trades", indexes = {
    @Index(name = "idx_option_trades_ticker_executed", columnList = "ticker, executed_at"),
    @Index(name = "idx_option_trades_option_chain", columnList = "option_chain"),
    @Index(name = "idx_option_trades_executed_at", columnList = "executed_at")
})
@IdClass(OptionTradeId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionTrade implements Persistable<OptionTradeId> {

    @Transient
    @Builder.Default
    private boolean isNewEntity = true;

    @Override
    @JsonIgnore
    public OptionTradeId getId() {
        return new OptionTradeId(tradeId, executedAt);
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return isNewEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNewEntity = false;
    }

    @Id
    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "price", nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "size", nullable = false)
    private Integer size;

    @Column(name = "nbbo_bid", precision = 12, scale = 4)
    private BigDecimal nbboBid;

    @Column(name = "nbbo_ask", precision = 12, scale = 4)
    private BigDecimal nbboAsk;

    @Column(name = "underlying_price", precision = 12, scale = 4)
    private BigDecimal underlyingPrice;

    @Column(name = "exchange", length = 50)
    private String exchange;

    @Id
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "option_chain", nullable = false, length = 50)
    private String optionChain;

    @Column(name = "condition", length = 50)
    private String condition;

    @Column(name = "is_multileg", nullable = false)
    @Builder.Default
    private Boolean isMultileg = false;

    @Column(name = "is_floor_trade", nullable = false)
    @Builder.Default
    private Boolean isFloorTrade = false;

    @Column(name = "is_sweep", nullable = false)
    @Builder.Default
    private Boolean isSweep = false;

    @Column(name = "aggressor", length = 50)
    private String aggressor;

    @Column(name = "premium", precision = 15, scale = 2)
    private BigDecimal premium;

    @Column(name = "side", length = 50)
    private String side;

    @Column(name = "expiry")
    private LocalDate expiry;

    @Column(name = "option_type", length = 10)
    private String optionType;

    @Column(name = "open_interest")
    private Integer openInterest;

    @Column(name = "strike", precision = 12, scale = 4)
    private BigDecimal strike;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "ewma_nbbo_ask", precision = 12, scale = 4)
    private BigDecimal ewmaNbboAsk;

    @Column(name = "ewma_nbbo_bid", precision = 12, scale = 4)
    private BigDecimal ewmaNbboBid;

    @Column(name = "implied_volatility", precision = 12, scale = 6)
    private BigDecimal impliedVolatility;

    @Column(name = "delta", precision = 12, scale = 6)
    private BigDecimal delta;

    @Column(name = "theta", precision = 12, scale = 6)
    private BigDecimal theta;

    @Column(name = "gamma", precision = 12, scale = 6)
    private BigDecimal gamma;

    @Column(name = "vega", precision = 12, scale = 6)
    private BigDecimal vega;

    @Column(name = "rho", precision = 12, scale = 6)
    private BigDecimal rho;

    @Column(name = "theo", precision = 12, scale = 4)
    private BigDecimal theo;

    @Column(name = "ask_vol")
    private Long askVol;

    @Column(name = "bid_vol")
    private Long bidVol;

    @Column(name = "no_side_vol")
    private Long noSideVol;

    @Column(name = "mid_vol")
    private Long midVol;

    @Column(name = "multi_vol")
    private Long multiVol;

    @Column(name = "stock_multi_vol")
    private Long stockMultiVol;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Needed because Lombok @Builder doesn't include @Transient fields by default
    // and isNewEntity must default to true for Kafka-ingested entities
}
