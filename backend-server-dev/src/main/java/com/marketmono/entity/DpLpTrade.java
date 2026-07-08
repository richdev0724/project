package com.marketmono.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dp_lp_trades", indexes = {
    @Index(name = "idx_dp_lp_trades_ticker_ts", columnList = "ticker, ts"),
    @Index(name = "idx_dp_lp_trades_venue_type_ts", columnList = "venue_type, ts"),
    @Index(name = "idx_dp_lp_trades_ticker_venue_ts", columnList = "ticker, venue_type, ts")
})
@IdClass(DpLpTradeId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpLpTrade implements Persistable<DpLpTradeId> {

    @Transient
    @Builder.Default
    private boolean isNewEntity = true;

    @Override
    @JsonIgnore
    public DpLpTradeId getId() {
        return new DpLpTradeId(id, ts);
    }

    public Long getTradeId() {
        return id;
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
    @Column(name = "id", insertable = false, updatable = false)
    private Long id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "venue_type", nullable = false, columnDefinition = "venue_type")
    private VenueType venueType;

    @Column(name = "price", nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "size", nullable = false)
    private Integer size;

    @Id
    @Column(name = "ts", nullable = false)
    private OffsetDateTime ts;

    @Column(name = "venue", length = 50)
    private String venue;

    @Column(name = "flags", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String flags;

    @Column(name = "ingest_source", length = 50)
    private String ingestSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
