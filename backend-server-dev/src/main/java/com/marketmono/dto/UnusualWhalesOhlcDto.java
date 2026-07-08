package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for upstream market data provider /api/stock/{ticker}/ohlc/{candleSize} endpoint response
 * Contains OHLC price data for ticker at specified candle intervals
 * 
 * Format differences:
 * - Daily candles (1d): Use 'date' field (YYYY-MM-DD), no start_time/end_time
 * - Intraday candles (1m, 5m, etc.): Use 'start_time' and 'end_time' fields (ISO timestamps)
 */
public record UnusualWhalesOhlcDto(
    List<OhlcItem> data
) {
    public record OhlcItem(
        // For intraday candles (1m, 5m, 10m, 15m, 30m, 1h, 4h)
        @JsonProperty("start_time")
        String startTime,
        
        @JsonProperty("end_time")
        String endTime,
        
        // For daily candles (1d) - date in YYYY-MM-DD format
        String date,
        
        // Common OHLC fields
        String open,
        String high,
        String low,
        String close,
        Long volume,
        
        @JsonProperty("total_volume")
        Long totalVolume,
        
        @JsonProperty("market_time")
        String marketTime
    ) {}
}
