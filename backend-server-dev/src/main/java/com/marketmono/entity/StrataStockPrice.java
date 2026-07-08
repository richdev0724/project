package com.marketmono.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "strata_stock_prices",
    indexes = {
        @Index(
            name = "idx_strata_stock_prices_display_latest",
            columnList = "display_ticker, tape_time DESC, fetched_at DESC"
        ),
        @Index(
            name = "idx_strata_stock_prices_source_latest",
            columnList = "source_ticker, tape_time DESC, fetched_at DESC"
        )
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrataStockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_ticker", nullable = false, length = 10)
    private String displayTicker;

    @Column(name = "source_ticker", nullable = false, length = 10)
    private String sourceTicker;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal closePrice;

    @Column(name = "prev_close_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal prevClosePrice;

    @Column(name = "change_percent", precision = 18, scale = 6)
    private BigDecimal changePercent;

    @Column(name = "open_price", precision = 18, scale = 6)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 18, scale = 6)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 6)
    private BigDecimal lowPrice;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "total_volume")
    private Long totalVolume;

    @Column(name = "market_time", length = 32)
    private String marketTime;

    @Column(name = "tape_time")
    private Instant tapeTime;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
