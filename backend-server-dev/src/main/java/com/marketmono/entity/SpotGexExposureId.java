package com.marketmono.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite primary key for SpotGexExposure.
 */
public class SpotGexExposureId implements Serializable {

    private String ticker;
    private LocalDate expiry;
    private BigDecimal strike;
    private Instant collectedAt;

    public SpotGexExposureId() {}

    public SpotGexExposureId(String ticker, LocalDate expiry, BigDecimal strike, Instant collectedAt) {
        this.ticker = ticker;
        this.expiry = expiry;
        this.strike = strike;
        this.collectedAt = collectedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpotGexExposureId that = (SpotGexExposureId) o;
        return Objects.equals(ticker, that.ticker)
            && Objects.equals(expiry, that.expiry)
            && Objects.equals(strike, that.strike)
            && Objects.equals(collectedAt, that.collectedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticker, expiry, strike, collectedAt);
    }
}
