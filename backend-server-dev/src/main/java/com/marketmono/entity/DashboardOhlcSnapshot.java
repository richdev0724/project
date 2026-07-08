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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One chart-ready OHLC series per (ticker, timeframe) for the dashboard
 * Market Overview / Magnificent Seven widgets. Replaced in place on every
 * collection cycle; rows older than the retention window are purged.
 */
@Entity
@Table(
    name = "dashboard_ohlc_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_dashboard_ohlc_snapshots_ticker_timeframe",
        columnNames = {"ticker", "timeframe"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOhlcSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "timeframe", nullable = false, length = 8)
    private String timeframe;

    @Column(name = "previous_session_close", precision = 18, scale = 6)
    private BigDecimal previousSessionClose;

    /** JSON array of series points (timestamp, date, open, high, low, close, volume). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "series", columnDefinition = "jsonb", nullable = false)
    private String series;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
