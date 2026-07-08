package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for market prices endpoint
 * Compatible with Polygon.io aggregates format
 */
public record MarketPricesResponse(
    @JsonProperty("ticker")
    String ticker,
    
    @JsonProperty("queryCount")
    int queryCount,
    
    @JsonProperty("resultsCount")
    int resultsCount,
    
    @JsonProperty("adjusted")
    boolean adjusted,
    
    @JsonProperty("results")
    List<PriceBar> results
) {
    public record PriceBar(
        @JsonProperty("v")
        Long volume,
        
        @JsonProperty("vw")
        BigDecimal volumeWeighted,
        
        @JsonProperty("o")
        BigDecimal open,
        
        @JsonProperty("c")
        BigDecimal close,
        
        @JsonProperty("h")
        BigDecimal high,
        
        @JsonProperty("l")
        BigDecimal low,
        
        @JsonProperty("t")
        long timestamp, // milliseconds since epoch
        
        @JsonProperty("n")
        Integer transactions
    ) {}
}
