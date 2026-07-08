package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dp_lp_aggregates", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_dp_lp_aggregates_ticker_venue_bucket", 
            columnNames = {"ticker", "venue_type", "bucket_start", "bucket_size"})
    },
    indexes = {
        @Index(name = "idx_dp_lp_aggregates_ticker_venue_bucket", 
            columnList = "ticker, venue_type, bucket_start")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpLpAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "venue_type", nullable = false, columnDefinition = "venue_type")
    private VenueType venueType;

    @Column(name = "bucket_start", nullable = false)
    private OffsetDateTime bucketStart;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "bucket_size", nullable = false, columnDefinition = "bucket_size")
    private BucketSize bucketSize;

    @Column(name = "trade_count", nullable = false)
    private Integer tradeCount;

    @Column(name = "total_volume", nullable = false)
    private Long totalVolume;

    @Column(name = "vwap", precision = 12, scale = 4)
    private BigDecimal vwap;

    @Column(name = "min_price", precision = 12, scale = 4)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 12, scale = 4)
    private BigDecimal maxPrice;

    @Column(name = "net_imbalance")
    private Long netImbalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
