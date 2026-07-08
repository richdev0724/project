package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DTO representing a single market tide data point with SPY price
 */
public record MarketTideDataPoint(
    @JsonProperty("timestamp")
    Long timestamp,
    
    @JsonProperty("date")
    String date,
    
    @JsonProperty("call_premium")
    BigDecimal callPremium,
    
    @JsonProperty("put_premium")
    BigDecimal putPremium,
    
    @JsonProperty("net_volume")
    Integer netVolume,
    
    @JsonProperty("spy_close")
    BigDecimal spyClose,
    
    @JsonProperty("original_timestamp")
    String originalTimestamp
) {
}
