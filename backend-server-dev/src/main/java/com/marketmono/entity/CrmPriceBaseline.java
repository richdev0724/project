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
 * One stock-state snapshot per CRM ticker per trading day, collected at
 * 9:30 AM ET Monday-Friday. The prev_close field is the baseline used to
 * compute % gain/loss against the live websocket price for the same ticker.
 */
@Entity
@Table(
    name = "crm_price_baselines",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_crm_price_baselines_ticker_date",
        columnNames = {"ticker", "snapshot_date"}
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmPriceBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "close", precision = 18, scale = 6)
    private BigDecimal close;

    @Column(name = "prev_close", precision = 18, scale = 6)
    private BigDecimal prevClose;

    @Column(name = "open", precision = 18, scale = 6)
    private BigDecimal open;

    @Column(name = "high", precision = 18, scale = 6)
    private BigDecimal high;

    @Column(name = "low", precision = 18, scale = 6)
    private BigDecimal low;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "total_volume")
    private Long totalVolume;

    @Column(name = "tape_time", length = 40)
    private String tapeTime;

    @Column(name = "market_time", length = 20)
    private String marketTime;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
