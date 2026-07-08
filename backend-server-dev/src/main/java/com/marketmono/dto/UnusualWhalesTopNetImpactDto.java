package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for upstream market data provider Top Net Impact API
 * Returns top tickers sorted by net premium (both bullish and bearish)
 */
public record UnusualWhalesTopNetImpactDto(
    @JsonProperty("data") List<TopNetImpactItem> data
) {
    /**
     * Individual ticker with net premium data
     */
    public record TopNetImpactItem(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("net_premium") Double netPremium
    ) {}
}
