package com.marketmono.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "spot_gex_exposures", indexes = {
    @Index(name = "idx_spot_gex_exposures_ticker_expiry_collected", columnList = "ticker, expiry, collected_at"),
    @Index(name = "idx_spot_gex_exposures_ticker_expiry_strike_collected", columnList = "ticker, expiry, strike, collected_at"),
    @Index(name = "idx_spot_gex_exposures_ticker_collected_desc", columnList = "ticker, collected_at")
})
@IdClass(SpotGexExposureId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpotGexExposure implements Persistable<SpotGexExposureId> {

    @Transient
    @Builder.Default
    private boolean isNewEntity = true;

    @Override
    @JsonIgnore
    public SpotGexExposureId getId() {
        return new SpotGexExposureId(ticker, expiry, strike, collectedAt);
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
    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Id
    @Column(name = "expiry", nullable = false)
    private LocalDate expiry;

    @Id
    @Column(name = "strike", nullable = false, precision = 12, scale = 4)
    private BigDecimal strike;

    @Id
    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "price", precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "date")
    private LocalDate marketDate;

    @Column(name = "source_time")
    private Instant sourceTime;

    @Column(name = "call_delta_oi", precision = 24, scale = 4)
    private BigDecimal callDeltaOi;

    @Column(name = "call_gamma_oi", precision = 24, scale = 4)
    private BigDecimal callGammaOi;

    @Column(name = "call_vanna_oi", precision = 24, scale = 4)
    private BigDecimal callVannaOi;

    @Column(name = "call_charm_oi", precision = 24, scale = 4)
    private BigDecimal callCharmOi;

    @Column(name = "put_delta_oi", precision = 24, scale = 4)
    private BigDecimal putDeltaOi;

    @Column(name = "put_gamma_oi", precision = 24, scale = 4)
    private BigDecimal putGammaOi;

    @Column(name = "put_vanna_oi", precision = 24, scale = 4)
    private BigDecimal putVannaOi;

    @Column(name = "put_charm_oi", precision = 24, scale = 4)
    private BigDecimal putCharmOi;

    @Column(name = "call_delta_vol", precision = 24, scale = 4)
    private BigDecimal callDeltaVol;

    @Column(name = "call_gamma_vol", precision = 24, scale = 4)
    private BigDecimal callGammaVol;

    @Column(name = "call_vanna_vol", precision = 24, scale = 4)
    private BigDecimal callVannaVol;

    @Column(name = "call_charm_vol", precision = 24, scale = 4)
    private BigDecimal callCharmVol;

    @Column(name = "put_delta_vol", precision = 24, scale = 4)
    private BigDecimal putDeltaVol;

    @Column(name = "put_gamma_vol", precision = 24, scale = 4)
    private BigDecimal putGammaVol;

    @Column(name = "put_vanna_vol", precision = 24, scale = 4)
    private BigDecimal putVannaVol;

    @Column(name = "put_charm_vol", precision = 24, scale = 4)
    private BigDecimal putCharmVol;

    @Column(name = "call_delta_bid", precision = 24, scale = 4)
    private BigDecimal callDeltaBid;

    @Column(name = "call_delta_ask", precision = 24, scale = 4)
    private BigDecimal callDeltaAsk;

    @Column(name = "call_gamma_bid", precision = 24, scale = 4)
    private BigDecimal callGammaBid;

    @Column(name = "call_gamma_ask", precision = 24, scale = 4)
    private BigDecimal callGammaAsk;

    @Column(name = "call_vanna_bid", precision = 24, scale = 4)
    private BigDecimal callVannaBid;

    @Column(name = "call_vanna_ask", precision = 24, scale = 4)
    private BigDecimal callVannaAsk;

    @Column(name = "call_charm_bid", precision = 24, scale = 4)
    private BigDecimal callCharmBid;

    @Column(name = "call_charm_ask", precision = 24, scale = 4)
    private BigDecimal callCharmAsk;

    @Column(name = "put_delta_bid", precision = 24, scale = 4)
    private BigDecimal putDeltaBid;

    @Column(name = "put_delta_ask", precision = 24, scale = 4)
    private BigDecimal putDeltaAsk;

    @Column(name = "put_gamma_bid", precision = 24, scale = 4)
    private BigDecimal putGammaBid;

    @Column(name = "put_gamma_ask", precision = 24, scale = 4)
    private BigDecimal putGammaAsk;

    @Column(name = "put_vanna_bid", precision = 24, scale = 4)
    private BigDecimal putVannaBid;

    @Column(name = "put_vanna_ask", precision = 24, scale = 4)
    private BigDecimal putVannaAsk;

    @Column(name = "put_charm_bid", precision = 24, scale = 4)
    private BigDecimal putCharmBid;

    @Column(name = "put_charm_ask", precision = 24, scale = 4)
    private BigDecimal putCharmAsk;
}
