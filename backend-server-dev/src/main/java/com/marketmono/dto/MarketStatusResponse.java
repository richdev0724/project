package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;

/**
 * Response DTO for market status endpoint
 * Provides market open/closed status and session information
 * Compatible with Polygon API market status response format
 */
public record MarketStatusResponse(
    @JsonProperty("exchanges")
    ExchangeStatus exchanges,
    
    @JsonProperty("market_time")
    String marketTime,
    
    @JsonProperty("timestamp")
    String timestamp
) {
    /**
     * Exchange status data matching Polygon API format
     */
    public record ExchangeStatus(
        @JsonProperty("nasdaq")
        String nasdaq,
        
        @JsonProperty("nyse")
        String nyse
    ) {}
    
    /**
     * Create market status response from stock state market_time field
     * 
     * @param marketTime Market session from upstream market data API ("premarket", "market", "postmarket")
     * @param timestamp ISO timestamp of the status check
     * @return MarketStatusResponse with Polygon-compatible format
     */
    public static MarketStatusResponse fromMarketTime(String marketTime, String timestamp) {
        String status = mapMarketTimeToStatus(marketTime);
        return new MarketStatusResponse(
            new ExchangeStatus(status, status),
            marketTime,
            timestamp
        );
    }
    
    /**
     * Map upstream market data API market_time values to Polygon API status format
     * 
     * @param marketTime upstream market data API market session
     * @return Polygon API compatible status
     */
    private static String mapMarketTimeToStatus(String marketTime) {
        return switch (marketTime.toLowerCase(Locale.ROOT)) {
            case "market", "regular" -> "open";
            case "premarket" -> "extended-hours";
            case "postmarket" -> "extended-hours";
            default -> "closed";
        };
    }
}
