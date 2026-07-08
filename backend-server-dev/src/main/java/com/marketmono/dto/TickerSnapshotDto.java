package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ticker snapshot response (Polygon API-compatible format)
 * Provides backward compatibility with frontend expecting Polygon format
 */
public record TickerSnapshotDto(
    @JsonProperty("ticker")
    String ticker,
    
    @JsonProperty("todaysChangePerc")
    Double todaysChangePerc,
    
    @JsonProperty("todaysChange")
    Double todaysChange,
    
    @JsonProperty("updated")
    Long updated,
    
    @JsonProperty("day")
    DayData day,
    
    @JsonProperty("prevDay")
    PrevDayData prevDay
) {
    public record DayData(
        @JsonProperty("c") String close,     // Close price
        @JsonProperty("h") String high,      // High price
        @JsonProperty("l") String low,       // Low price
        @JsonProperty("o") String open,      // Open price
        @JsonProperty("v") Long volume       // Volume
    ) {}
    
    public record PrevDayData(
        @JsonProperty("c") String close      // Previous close price
    ) {}
    
    /**
     * Create Polygon-compatible response from upstream market data API StockStateResponse
     * 
     * @param ticker Stock ticker symbol
     * @param stockState upstream market data API stock state response
     * @return Polygon-compatible ticker snapshot
     */
    public static TickerSnapshotDto fromStockState(String ticker, StockStateResponse stockState) {
        if (stockState == null || stockState.data() == null) {
            return null;
        }
        
        var data = stockState.data();
        
        // Calculate percentage change
        Double changePerc = calculatePercentageChange(data.close(), data.prevClose());
        
        // Calculate absolute change
        Double change = calculateAbsoluteChange(data.close(), data.prevClose());
        
        // Use tape_time as timestamp (convert to Unix timestamp in milliseconds)
        Long updated = System.currentTimeMillis(); // Fallback to current time
        
        DayData day = new DayData(
            data.close(),
            data.high(),
            data.low(),
            data.open(),
            data.totalVolume()
        );
        
        PrevDayData prevDay = new PrevDayData(data.prevClose());
        
        return new TickerSnapshotDto(
            ticker,
            changePerc,
            change,
            updated,
            day,
            prevDay
        );
    }
    
    /**
     * Calculate percentage change between current and previous close
     */
    private static Double calculatePercentageChange(String currentClose, String prevClose) {
        try {
            BigDecimal current = new BigDecimal(currentClose);
            BigDecimal prev = new BigDecimal(prevClose);
            
            if (prev.compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }
            
            BigDecimal change = current.subtract(prev);
            BigDecimal percentChange = change.divide(prev, 6, RoundingMode.HALF_UP)
                                             .multiply(new BigDecimal("100"));
            
            return percentChange.doubleValue();
        } catch (NumberFormatException | ArithmeticException e) {
            return 0.0;
        }
    }
    
    /**
     * Calculate absolute change between current and previous close
     */
    private static Double calculateAbsoluteChange(String currentClose, String prevClose) {
        try {
            BigDecimal current = new BigDecimal(currentClose);
            BigDecimal prev = new BigDecimal(prevClose);
            return current.subtract(prev).doubleValue();
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
