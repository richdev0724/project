package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for upstream market data provider market tide API response
 */
public record UnusualWhalesMarketTideDto(
    @JsonProperty("data")
    List<MarketTideItem> data
) {
    public record MarketTideItem(
        @JsonProperty("timestamp")
        String timestamp,
        
        @JsonProperty("date")
        String date,
        
        @JsonProperty("net_call_premium")
        BigDecimal netCallPremium,
        
        @JsonProperty("net_put_premium")
        BigDecimal netPutPremium,
        
        @JsonProperty("net_volume")
        Integer netVolume
    ) {
    }
}
