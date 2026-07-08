package com.marketmono.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "flow_alerts", indexes = {
    @Index(name = "idx_flow_alerts_ticker", columnList = "ticker"),
    @Index(name = "idx_flow_alerts_expiry", columnList = "expiry_date"),
    @Index(name = "idx_flow_alerts_trade_time", columnList = "trade_start_time"),
    @Index(name = "idx_flow_alerts_alert_rule", columnList = "alert_rule"),
    @Index(name = "idx_flow_alerts_ticker_expiry", columnList = "ticker, expiry_date"),
    @Index(name = "idx_flow_alerts_created_at", columnList = "created_at")
})
@IdClass(FlowAlertId.class)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowAlert implements Persistable<FlowAlertId> {

    @Transient
    @Builder.Default
    private boolean isNewEntity = true;

    @Override
    @JsonIgnore
    public FlowAlertId getId() {
        return new FlowAlertId(alertId, tradeStartTime);
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
    @Column(name = "alert_id", nullable = false)
    private UUID alertId;
    
    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;
    
    @Column(name = "option_symbol", nullable = false, length = 50)
    private String optionSymbol;
    
    @Column(name = "strike", nullable = false, precision = 12, scale = 2)
    private BigDecimal strike;
    
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;
    
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "option_type", nullable = false, columnDefinition = "option_type_enum")
    private OptionTypeEnum optionType;
    
    @Column(name = "price", precision = 12, scale = 4)
    private BigDecimal price;
    
    @Column(name = "bid", precision = 12, scale = 4)
    private BigDecimal bid;
    
    @Column(name = "ask", precision = 12, scale = 4)
    private BigDecimal ask;
    
    @Column(name = "underlying_price", precision = 12, scale = 4)
    private BigDecimal underlyingPrice;
    
    @Column(name = "total_size")
    private Integer totalSize;
    
    @Column(name = "total_premium", precision = 15, scale = 2)
    private BigDecimal totalPremium;
    
    @Column(name = "trade_count")
    private Integer tradeCount;
    
    @Column(name = "volume")
    private Integer volume;
    
    @Column(name = "open_interest")
    private Integer openInterest;
    
    @Column(name = "volume_oi_ratio", precision = 15, scale = 2)
    private BigDecimal volumeOiRatio;
    
    @Column(name = "implied_volatility_start", precision = 10, scale = 6)
    private BigDecimal impliedVolatilityStart;
    
    @Column(name = "implied_volatility_end", precision = 10, scale = 6)
    private BigDecimal impliedVolatilityEnd;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_rule", length = 50)
    private AlertRule alertRule;
    
    @Column(name = "has_sweep")
    @Builder.Default
    private Boolean hasSweep = false;
    
    @Column(name = "has_floor")
    @Builder.Default
    private Boolean hasFloor = false;
    
    @Column(name = "has_singleleg")
    @Builder.Default
    private Boolean hasSingleleg = false;
    
    @Column(name = "has_multileg")
    @Builder.Default
    private Boolean hasMultileg = false;
    
    @Column(name = "all_opening_trades")
    @Builder.Default
    private Boolean allOpeningTrades = false;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private Sector sector;
    
    @Column(name = "market_cap")
    private Long marketCap;
    
    @Column(name = "next_earnings_date")
    private LocalDate nextEarningsDate;
    
    @Id
    @Column(name = "trade_start_time", nullable = false)
    private Instant tradeStartTime;
    
    @Column(name = "trade_end_time")
    private Instant tradeEndTime;
    
    @Column(name = "total_ask_side_premium", precision = 15, scale = 2)
    private BigDecimal totalAskSidePremium;
    
    @Column(name = "total_bid_side_premium", precision = 15, scale = 2)
    private BigDecimal totalBidSidePremium;
    
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "data_source", nullable = false, columnDefinition = "data_source_enum")
    @Builder.Default
    private DataSourceEnum dataSource = DataSourceEnum.REST;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
