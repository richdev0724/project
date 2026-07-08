package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SectorPerformanceResponse(
    String ticker,
    String sector,
    PriceChanges priceChanges,
    PercentageChanges percentageChanges,
    Double currentPrice
) {
    public record PriceChanges(
        @JsonProperty("1d") Double oneDay,
        @JsonProperty("30d") Double thirtyDay,
        @JsonProperty("6m") Double sixMonth
    ) {}
    
    public record PercentageChanges(
        @JsonProperty("1d") Double oneDay,
        @JsonProperty("30d") Double thirtyDay,
        @JsonProperty("6m") Double sixMonth
    ) {}
}
