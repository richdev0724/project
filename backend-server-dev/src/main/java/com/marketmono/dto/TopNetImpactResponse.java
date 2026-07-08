package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for Top Net Impact endpoint
 * Returns separated lists of bullish and bearish tickers
 */
public record TopNetImpactResponse(
    @JsonProperty("success") boolean success,
    @JsonProperty("data") TopNetImpactData data,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("error") String error
) {
    public static TopNetImpactResponse success(TopNetImpactData data) {
        return new TopNetImpactResponse(true, data, Instant.now(), null);
    }
    
    public static TopNetImpactResponse error(String errorMessage) {
        return new TopNetImpactResponse(false, null, Instant.now(), errorMessage);
    }
    
    /**
     * Data container with bullish and bearish ticker lists
     */
    public record TopNetImpactData(
        @JsonProperty("bullish") List<TopNetImpactTicker> bullish,
        @JsonProperty("bearish") List<TopNetImpactTicker> bearish
    ) {}
    
    /**
     * Individual ticker data for frontend display
     */
    public record TopNetImpactTicker(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("net_premium") Double netPremium
    ) {}
}
