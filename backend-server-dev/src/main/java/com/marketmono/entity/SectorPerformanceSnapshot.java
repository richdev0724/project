package com.marketmono.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One performance snapshot per sector ETF for the dashboard Sectors widget.
 * The live fields (price, prev close, volume, daily %) come from the upstream
 * stock-state endpoint every minute during market hours; the 30-day and
 * 6-month baseline closes are refreshed once per trading day from daily OHLC
 * history. Replaced in place on each collection cycle.
 */
@Entity
@Table(
    name = "sector_performance_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sector_performance_snapshots_ticker",
        columnNames = {"ticker"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorPerformanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "last_price", precision = 18, scale = 6)
    private BigDecimal lastPrice;

    @Column(name = "prev_close", precision = 18, scale = 6)
    private BigDecimal prevClose;

    @Column(name = "daily_pct")
    private Double dailyPct;

    @Column(name = "pct_30d")
    private Double pct30d;

    @Column(name = "pct_6m")
    private Double pct6m;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "last_date")
    private LocalDate lastDate;

    @Column(name = "baseline_30d_close", precision = 18, scale = 6)
    private BigDecimal baseline30dClose;

    @Column(name = "baseline_6m_close", precision = 18, scale = 6)
    private BigDecimal baseline6mClose;

    @Column(name = "baseline_date")
    private LocalDate baselineDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
