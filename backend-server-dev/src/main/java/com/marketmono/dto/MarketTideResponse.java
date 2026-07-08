package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Response wrapper for market tide data
 */
public record MarketTideResponse(
    @JsonProperty("success")
    boolean success,
    
    @JsonProperty("data")
    List<MarketTideDataPoint> data,
    
    @JsonProperty("timestamp")
    String timestamp,
    
    @JsonProperty("error")
    String error
) implements ApiResponse<List<MarketTideDataPoint>> {
    /**
     * Success response constructor
     */
    public static MarketTideResponse success(List<MarketTideDataPoint> data) {
        return new MarketTideResponse(
            true,
            data,
            Instant.now().toString(),
            null
        );
    }
    
    /**
     * Error response constructor
     */
    public static MarketTideResponse error(String errorMessage) {
        return new MarketTideResponse(
            false,
            null,
            Instant.now().toString(),
            errorMessage
        );
    }
}
