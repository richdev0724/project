package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily options volume & premium snapshot per ticker, collected from the
 * upstream market data API by the ticker-options-volume sync scheduler.
 * One row per (ticker, date); the current trading day's row is upserted
 * every sync cycle as intraday numbers evolve.
 */
@Entity
@Table(name = "ticker_options_volume",
    uniqueConstraints = @UniqueConstraint(name = "uq_ticker_options_volume_ticker_date", columnNames = {"ticker", "date"})
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TickerOptionsVolume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "call_volume")
    private Long callVolume;

    @Column(name = "put_volume")
    private Long putVolume;

    @Column(name = "call_premium", precision = 20, scale = 4)
    private BigDecimal callPremium;

    @Column(name = "put_premium", precision = 20, scale = 4)
    private BigDecimal putPremium;

    @Column(name = "call_open_interest")
    private Long callOpenInterest;

    @Column(name = "put_open_interest")
    private Long putOpenInterest;

    @Column(name = "call_volume_ask_side")
    private Long callVolumeAskSide;

    @Column(name = "call_volume_bid_side")
    private Long callVolumeBidSide;

    @Column(name = "put_volume_ask_side")
    private Long putVolumeAskSide;

    @Column(name = "put_volume_bid_side")
    private Long putVolumeBidSide;

    @Column(name = "bullish_premium", precision = 20, scale = 4)
    private BigDecimal bullishPremium;

    @Column(name = "bearish_premium", precision = 20, scale = 4)
    private BigDecimal bearishPremium;

    @Column(name = "net_call_premium", precision = 20, scale = 4)
    private BigDecimal netCallPremium;

    @Column(name = "net_put_premium", precision = 20, scale = 4)
    private BigDecimal netPutPremium;

    @Column(name = "avg_3_day_call_volume", precision = 20, scale = 4)
    private BigDecimal avg3DayCallVolume;

    @Column(name = "avg_3_day_put_volume", precision = 20, scale = 4)
    private BigDecimal avg3DayPutVolume;

    @Column(name = "avg_7_day_call_volume", precision = 20, scale = 4)
    private BigDecimal avg7DayCallVolume;

    @Column(name = "avg_7_day_put_volume", precision = 20, scale = 4)
    private BigDecimal avg7DayPutVolume;

    @Column(name = "avg_30_day_call_volume", precision = 20, scale = 4)
    private BigDecimal avg30DayCallVolume;

    @Column(name = "avg_30_day_put_volume", precision = 20, scale = 4)
    private BigDecimal avg30DayPutVolume;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
