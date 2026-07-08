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

import java.time.Instant;
import java.time.LocalDate;

/**
 * One top net impact snapshot per trading day for the dashboard Top Bullish
 * and Top Bearish Net Premium widgets. The current day's row is replaced in
 * place on every collection cycle; rows older than the retention window are
 * purged.
 */
@Entity
@Table(
    name = "top_net_impact_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_top_net_impact_snapshots_trading_date",
        columnNames = {"trading_date"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopNetImpactSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    /** JSON array of TopNetImpactTicker sorted by net premium descending. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bullish", columnDefinition = "jsonb", nullable = false)
    private String bullish;

    /** JSON array of TopNetImpactTicker sorted by net premium ascending (most negative first). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bearish", columnDefinition = "jsonb", nullable = false)
    private String bearish;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
