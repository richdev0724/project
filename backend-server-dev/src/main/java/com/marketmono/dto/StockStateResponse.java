package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for upstream market data provider /stock/:ticker/stock-state endpoint
 * Maps actual upstream market data API fields: close, prev_close, tape_time, volume, total_volume, open, high, low, market_time
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockStateResponse(
    @JsonProperty("data")
    StockStateData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockStateData(
        @JsonProperty("close")
        String close,           // Current/last price (String from API)
        
        @JsonProperty("prev_close")
        String prevClose,       // Previous close price
        
        @JsonProperty("tape_time")
        String tapeTime,        // Timestamp of data
        
        @JsonProperty("volume")
        Long volume,            // Current volume
        
        @JsonProperty("total_volume")
        Long totalVolume,       // Total day volume
        
        @JsonProperty("open")
        String open,            // Opening price
        
        @JsonProperty("high")
        String high,            // Day high
        
        @JsonProperty("low")
        String low,             // Day low
        
        @JsonProperty("market_time")
        String marketTime       // Market session: premarket/market/postmarket
    ) {}
}
