package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for upstream market data provider SPY OHLC API response
 */
public record UnusualWhalesSpyOhlcDto(
    @JsonProperty("data")
    List<SpyOhlcItem> data
) {
    public record SpyOhlcItem(
        @JsonProperty("start_time")
        String startTime,
        
        @JsonProperty("open")
        BigDecimal open,
        
        @JsonProperty("high")
        BigDecimal high,
        
        @JsonProperty("low")
        BigDecimal low,
        
        @JsonProperty("close")
        BigDecimal close,
        
        @JsonProperty("volume")
        Long volume
    ) {
    }
}
