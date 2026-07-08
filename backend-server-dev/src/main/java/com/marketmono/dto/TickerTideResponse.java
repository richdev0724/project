package com.marketmono.dto;

import java.time.Instant;
import java.util.List;

public record TickerTideResponse(
    boolean success,
    List<TickerTideDataPoint> data,
    String timestamp,
    String error
) implements ApiResponse<List<TickerTideDataPoint>> {
    public static TickerTideResponse success(List<TickerTideDataPoint> data) {
        return new TickerTideResponse(true, data, Instant.now().toString(), null);
    }
    
    public static TickerTideResponse error(String message) {
        return new TickerTideResponse(false, List.of(), Instant.now().toString(), message);
    }
}
