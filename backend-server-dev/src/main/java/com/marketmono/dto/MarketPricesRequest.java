package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for market prices endpoint
 */
public record MarketPricesRequest(
    @JsonProperty("tickers")
    List<String> tickers,
    
    @JsonProperty("timeframe")
    String timeframe, // 1D, 5D, 1M, 3M, 6M, YTD, 1Y, 5Y
    
    @JsonProperty("date")
    LocalDate date // Optional: specific date for historical data
) {
    public MarketPricesRequest {
        if (tickers == null || tickers.isEmpty()) {
            throw new IllegalArgumentException("Tickers list cannot be null or empty");
        }
        if (timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("Timeframe cannot be null or blank");
        }
    }
}
