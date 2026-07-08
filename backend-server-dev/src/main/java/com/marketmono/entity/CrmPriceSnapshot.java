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

/**
 * Latest Unusual Whales {@code price:<TICKER>} websocket tick per CRM ticker.
 * One row per ticker, upserted on a short flush interval so live prices
 * survive restarts. Feeds the Strata page over STOMP instead of REST polling.
 */
@Entity
@Table(
    name = "crm_price_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_crm_price_snapshots_ticker",
        columnNames = "ticker"
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmPriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "close", nullable = false, precision = 18, scale = 6)
    private BigDecimal close;

    @Column(name = "tick_time", nullable = false)
    private Instant tickTime;

    @Column(name = "volume")
    private Long volume;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
