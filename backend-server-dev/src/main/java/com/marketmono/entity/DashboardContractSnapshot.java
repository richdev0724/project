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
 * One option contract screener snapshot per (widget, sort order, trading day)
 * for the dashboard Top Contracts / Top ETF Orders / Top Sweep Orders widgets.
 * The current day's rows are replaced in place on every collection cycle;
 * rows older than the retention window are purged.
 */
@Entity
@Table(
    name = "dashboard_contract_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_dashboard_contract_snapshots",
        columnNames = {"widget", "sort_order", "trading_date"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardContractSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Widget key: top_contracts, top_etf_orders, or top_sweep_orders. */
    @Column(name = "widget", nullable = false, length = 32)
    private String widget;

    /** Upstream order param used for this variant: premium, volume, or stock_price. */
    @Column(name = "sort_order", nullable = false, length = 32)
    private String sortOrder;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    /** JSON array of raw OptionContractDto items as returned by the upstream screener. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private String data;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
