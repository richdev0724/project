package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling market tide data operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketTideService {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long ONE_MINUTE_MILLIS = 60_000L;
    private static final long TWO_MINUTES_MILLIS = 120_000L;
    
    /**
     * Record to hold the results of fetching market tide and SPY data for a single date
     */
    private record MarketDateFetchResult(
        List<UnusualWhalesMarketTideDto.MarketTideItem> marketTideData,
        List<UnusualWhalesSpyOhlcDto.SpyOhlcItem> spyOhlcData
    ) {}
    
    private final UnusualWhalesClient unusualWhalesClient;
    private final MarketTideSnapshotService marketTideSnapshotService;
    private final TopNetImpactSnapshotService topNetImpactSnapshotService;
    
    /**
     * Get market tide data with optional date range
     * 
     * The default request (no date range) powers the dashboard Mono Matrix and
     * is served database-only from market_tide_snapshots (populated by the
     * per-minute scheduler); it never calls the upstream market data API.
     * Explicit date-range requests still fetch historical data upstream.
     * 
     * @param date Optional start date (YYYY-MM-DD)
     * @param endDate Optional end date (YYYY-MM-DD)
     * @return MarketTideResponse containing market tide data with SPY prices
     */
    public MarketTideResponse getMarketTide(String date, String endDate) {
        if (date == null || endDate == null) {
            log.debug("[MARKET] Serving current-day tide from market_tide_snapshots");
            return marketTideSnapshotService.getMarketTideFromDatabase();
        }

        try {
            log.info("[MARKET] {} to {}", date, endDate);
            
            List<UnusualWhalesMarketTideDto.MarketTideItem> allMarketTideData = new ArrayList<>();
            List<UnusualWhalesSpyOhlcDto.SpyOhlcItem> allSpyOhlcData = new ArrayList<>();
            
            List<String> tradingDates = generateTradingDates(date, endDate);
            log.info("[MARKET] {} days", tradingDates.size());
            
            // Fetch data for each date using parallel streams for concurrent execution
            List<CompletableFuture<MarketDateFetchResult>> futures = tradingDates.stream()
                .map(dateStr -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return fetchMarketDataForDate(dateStr);
                    } catch (Exception e) {
                        log.debug("Skipping date {}: {}", dateStr, e.getMessage());
                        return new MarketDateFetchResult(List.of(), List.of()); // Return empty results on failure
                    }
                }))
                .toList();
            
            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // Collect results from all futures
            for (var future : futures) {
                try {
                    MarketDateFetchResult result = future.get();
                    allMarketTideData.addAll(result.marketTideData());
                    allSpyOhlcData.addAll(result.spyOhlcData());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.debug("Interrupted while collecting market tide result: {}", e.getMessage());
                    break;
                } catch (Exception e) {
                    log.debug("Failed to get result from future: {}", e.getMessage());
                }
            }
            
            log.info("Total: {} market tide points, {} SPY OHLC points", 
                allMarketTideData.size(), allSpyOhlcData.size());
            
            // Merge and transform data
            List<MarketTideDataPoint> transformedData = mergeAndTransformData(
                allMarketTideData, 
                allSpyOhlcData
            );
            
            log.info("[MARKET] {} - transformed", transformedData.size());
            
            // Count how many have SPY prices
            long withSpyPrice = transformedData.stream()
                .filter(d -> d.spyClose() != null)
                .count();
            log.info("[MARKET] {}/{} with SPY", withSpyPrice, transformedData.size());
            
            return MarketTideResponse.success(transformedData);
            
        } catch (Exception e) {
            log.warn("Error fetching market tide data: {}", e.getMessage());
            return MarketTideResponse.error(e.getMessage());
        }
    }
    
    /**
     * Generate list of trading dates (excluding weekends) between start and end date
     */
    private List<String> generateTradingDates(String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate, DATE_FORMATTER);
        LocalDate end = LocalDate.parse(endDate, DATE_FORMATTER);
        
        List<String> tradingDates = new ArrayList<>();
        
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            // Skip weekends
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && 
                date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                tradingDates.add(date.format(DATE_FORMATTER));
            }
        }
        
        return tradingDates;
    }
    
    /**
     * Fetch market tide and SPY OHLC data for a specific date (used in parallel execution)
     */
    private MarketDateFetchResult fetchMarketDataForDate(String dateStr) {
        List<UnusualWhalesMarketTideDto.MarketTideItem> marketTideData = new ArrayList<>();
        List<UnusualWhalesSpyOhlcDto.SpyOhlcItem> spyOhlcData = new ArrayList<>();
        
        // Fetch market tide data
        UnusualWhalesMarketTideDto marketTideResponse = 
            unusualWhalesClient.getMarketTide(false, false, dateStr);
        
        // Fetch SPY OHLC data
        UnusualWhalesSpyOhlcDto spyOhlcResponse = 
            unusualWhalesClient.getSpyOhlc(dateStr, "1m");
        
        if (marketTideResponse != null && marketTideResponse.data() != null) {
            marketTideData.addAll(marketTideResponse.data());
            log.debug("Fetched {} market tide points for {}", 
                marketTideResponse.data().size(), dateStr);
        }
        
        if (spyOhlcResponse != null && spyOhlcResponse.data() != null) {
            spyOhlcData.addAll(spyOhlcResponse.data());
            log.debug("Fetched {} SPY OHLC points for {}", 
                spyOhlcResponse.data().size(), dateStr);
        }
        
        return new MarketDateFetchResult(marketTideData, spyOhlcData);
    }
    
    /**
     * Merge market tide data with SPY OHLC data by timestamp
     */
    private List<MarketTideDataPoint> mergeAndTransformData(
            List<UnusualWhalesMarketTideDto.MarketTideItem> marketTideData,
            List<UnusualWhalesSpyOhlcDto.SpyOhlcItem> spyOhlcData) {
        
        log.debug("Merging {} market tide points with {} SPY OHLC points", 
            marketTideData.size(), spyOhlcData.size());
        
        // Create SPY price map with flexible timestamp matching
        Map<Long, BigDecimal> spyPriceMap = new HashMap<>();
        
        for (var item : spyOhlcData) {
            long timestamp = parseTimestamp(item.startTime());
            spyPriceMap.put(timestamp, item.close());
            
            // Also add rounded timestamp for better matching (round to nearest minute)
            long roundedTimestamp = (timestamp / ONE_MINUTE_MILLIS) * ONE_MINUTE_MILLIS;
            spyPriceMap.putIfAbsent(roundedTimestamp, item.close());
        }
        
        log.debug("Created SPY price map with {} entries", spyPriceMap.size());
        
        // Transform market tide data and merge with SPY prices
        List<MarketTideDataPoint> result = marketTideData.stream()
            .map(item -> {
                long timestamp = parseTimestamp(item.timestamp());
                long roundedTimestamp = (timestamp / ONE_MINUTE_MILLIS) * ONE_MINUTE_MILLIS;
                
                // Try exact match first, then rounded timestamp
                BigDecimal spyClose = spyPriceMap.get(timestamp);
                if (spyClose == null) {
                    spyClose = spyPriceMap.get(roundedTimestamp);
                }
                
                // If still no match, find closest timestamp within 2 minutes
                if (spyClose == null) {
                    spyClose = findClosestSpyPrice(timestamp, spyPriceMap);
                }
                
                return new MarketTideDataPoint(
                    timestamp,
                    item.date(),
                    item.netCallPremium(),
                    item.netPutPremium(),
                    item.netVolume(),
                    spyClose,
                    item.timestamp()
                );
            })
            .toList();
        
        // Count matches for logging
        long withSpyPrice = result.stream()
            .filter(d -> d.spyClose() != null)
            .count();
        log.debug("{} out of {} data points have SPY prices", withSpyPrice, result.size());
        
        return result;
    }
    
    /**
     * Find closest SPY price within 2 minutes window
     */
    private BigDecimal findClosestSpyPrice(long timestamp, Map<Long, BigDecimal> spyPriceMap) {
        for (long offset = -TWO_MINUTES_MILLIS; offset <= TWO_MINUTES_MILLIS; offset += ONE_MINUTE_MILLIS) {
            long testTimestamp = timestamp + offset;
            BigDecimal price = spyPriceMap.get(testTimestamp);
            if (price != null) {
                return price;
            }
        }
        return null;
    }
    
    /**
     * Parse ISO timestamp string to milliseconds
     */
    private long parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            log.debug("Error parsing timestamp {}: {}", timestamp, e.getMessage());
            return 0L;
        }
    }
    
    /**
     * Get top net impact tickers (bullish and bearish) for the dashboard
     * Top Bullish / Top Bearish Net Premium widgets.
     * 
     * Served database-only from top_net_impact_snapshots (populated by the
     * per-minute scheduler); user requests never call the upstream market
     * data API.
     * 
     * @return TopNetImpactResponse containing bullish and bearish ticker lists
     */
    public TopNetImpactResponse getTopNetImpact() {
        return topNetImpactSnapshotService.getTopNetImpactFromDatabase();
    }

}
