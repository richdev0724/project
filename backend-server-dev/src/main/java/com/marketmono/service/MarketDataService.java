package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.MarketPricesResponse;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Service for retrieving and formatting stock price data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {
      
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get market prices for a ticker within a specific timeframe
     * 
     * @param ticker Stock ticker symbol
     * @param timeframe Timeframe (1D, 5D, 1M, 3M, 6M, YTD, 1Y, 5Y)
     * @param date Optional specific date
     * @return MarketPricesResponse in Polygon-compatible format
     */
    @Cacheable(value = "marketPrices", key = "#ticker + '-' + #timeframe + '-' + #date")
    public MarketPricesResponse getMarketPrices(String ticker, String timeframe, LocalDate date) {
        log.debug("Fetching market prices for {} with timeframe {}", ticker, timeframe);
        
        String candleSize = determineCandleSize(timeframe);
        String uwTimeframe = mapToUwTimeframe(timeframe);
        String dateStr = date != null ? date.toString() : null;
        
        // If 1D and no date provided, use current date.
        // upstream market data API for intraday usually requires date.
        if ("1D".equalsIgnoreCase(timeframe) && dateStr == null) {
            dateStr = DateUtils.getCurrentDateInEasternTime().toString();
        }

        UnusualWhalesOhlcDto ohlcData = unusualWhalesClient.getOhlcData(
            ticker,
            candleSize,
            dateStr,
            uwTimeframe,
            null,
            null
        );
        
        List<MarketPricesResponse.PriceBar> bars = (ohlcData != null && ohlcData.data() != null) 
            ? ohlcData.data().stream().map(this::toPriceBar).toList()
            : Collections.emptyList();
        
        return new MarketPricesResponse(
            ticker,
            bars.size(),
            bars.size(),
            true, // adjusted is always true
            bars
        );
    }
    
    private String determineCandleSize(String timeframe) {
        if (timeframe == null) return "1d";
        return switch (timeframe.toUpperCase()) {
            case "1D" -> "5m"; // Intraday 5m candles for 1 day view
            case "5D" -> "15m"; // Intraday 15m candles for 5 day view
            case "1M" -> "1h"; // Hourly candles for 1 month view
            default -> "1d"; // Daily candles for longer timeframes
        };
    }

    private String mapToUwTimeframe(String timeframe) {
        if (timeframe == null) return null;
        return switch (timeframe.toUpperCase()) {
            case "1D", "5D" -> null; // Intraday queries use date/end_date, not timeframe param
            default -> timeframe.toUpperCase(); // Pass through 1M, 3M, YTD, 1Y, etc.
        };
    }
    
    /**
     * Convert UnusualWhalesOhlcDto.OhlcItem to PriceBar DTO
     */
    private MarketPricesResponse.PriceBar toPriceBar(UnusualWhalesOhlcDto.OhlcItem item) {
        long timestamp;
        if (item.startTime() != null) {
            // ISO timestamp for intraday
            timestamp = LocalDateTime.parse(item.startTime(), DateTimeFormatter.ISO_DATE_TIME)
                .atZone(EST_ZONE)
                .toInstant()
                .toEpochMilli();
        } else if (item.date() != null) {
            // YYYY-MM-DD for daily
            timestamp = LocalDate.parse(item.date())
                .atStartOfDay(EST_ZONE)
                .toInstant()
                .toEpochMilli();
        } else {
            timestamp = 0;
        }
        
        return new MarketPricesResponse.PriceBar(
            item.volume(),
            BigDecimal.ZERO, // VWAP not provided by upstream OHLC
            new BigDecimal(item.open()),
            new BigDecimal(item.close()),
            new BigDecimal(item.high()),
            new BigDecimal(item.low()),
            timestamp,
            0 // Transactions count not provided
        );
    }
}
