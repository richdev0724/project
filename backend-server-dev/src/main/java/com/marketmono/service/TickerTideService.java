package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickerTideService {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * Record to hold the results of fetching data for a single date
     */
    private record DateFetchResult(
        List<UnusualWhalesTickerTideDto.TickerTideItem> tickerTideData,
        List<UnusualWhalesTickerOhlcDto.TickerOhlcItem> tickerOhlcData
    ) {}
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get ticker-specific tide data for a single date or date range
     */
    public TickerTideResponse getTickerTide(String ticker, String date, String endDate) {
        try {
            List<String> datesToFetch;
            
            if (date != null && endDate != null) {
                // Generate trading days between start and end date
                datesToFetch = generateTradingDates(LocalDate.parse(date), LocalDate.parse(endDate));
                log.info("[TICKER] {} - {} to {} ({} days)", ticker, date, endDate, datesToFetch.size());
            } else if (date != null) {
                datesToFetch = List.of(date);
                log.info("[TICKER] {} - {}", ticker, date);
            } else {
                datesToFetch = List.of(); // Will fetch current/latest data
                log.info("[TICKER] {} - current", ticker);
            }
            
            List<UnusualWhalesTickerTideDto.TickerTideItem> allTickerTideData = new ArrayList<>();
            List<UnusualWhalesTickerOhlcDto.TickerOhlcItem> allTickerOhlcData = new ArrayList<>();
            
            if (datesToFetch.isEmpty()) {
                // Fetch without date parameter (current data)
                var tickerTideResponse = unusualWhalesClient.getTickerTide(ticker, null);
                var tickerOhlcResponse = unusualWhalesClient.getTickerOhlc(ticker, null, "1m");
                
                if (tickerTideResponse != null && tickerTideResponse.data() != null) {
                    allTickerTideData.addAll(tickerTideResponse.data());
                }
                if (tickerOhlcResponse != null && tickerOhlcResponse.data() != null) {
                    allTickerOhlcData.addAll(tickerOhlcResponse.data());
                }
            } else {
                // Fetch data for each date using parallel streams for concurrent execution
                List<CompletableFuture<DateFetchResult>> futures = datesToFetch.stream()
                    .map(dateStr -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return fetchDataForDate(ticker, dateStr);
                        } catch (Exception e) {
                            log.warn("Failed to fetch data for {} on {}: {}", ticker, dateStr, e.getMessage());
                            return new DateFetchResult(List.of(), List.of()); // Return empty results on failure
                        }
                    }))
                    .toList();
                
                // Wait for all futures to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                // Collect results from all futures
                for (var future : futures) {
                    try {
                        DateFetchResult result = future.get();
                        allTickerTideData.addAll(result.tickerTideData());
                        allTickerOhlcData.addAll(result.tickerOhlcData());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while collecting ticker tide result: {}", e.getMessage());
                        break;
                    } catch (Exception e) {
                        log.warn("Failed to get result from future: {}", e.getMessage());
                    }
                }
                
                log.info("Successfully fetched data for {} dates in parallel", datesToFetch.size());
                
                log.info("[TICKER] {} dates - fetched", datesToFetch.size());
            }
            
            if (allTickerTideData.isEmpty()) {
                log.warn("No ticker tide data found for {}", ticker);
                return TickerTideResponse.error("No data found for ticker: " + ticker);
            }
            
            // Transform and merge data
            List<TickerTideDataPoint> transformedData = mergeAndTransformData(
                ticker, allTickerTideData, allTickerOhlcData);
            
            log.info("[TICKER] {} - {} points", ticker, transformedData.size());
            return TickerTideResponse.success(transformedData);
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("No data found")) {
                log.warn("No ticker tide data for {}", ticker);
            } else {
                log.debug("Error fetching ticker tide data for {}: {}", ticker, e.getMessage());
            }
            return TickerTideResponse.error("Failed to fetch data: " + e.getMessage());
        }
    }
    
    /**
     * Generate list of trading dates (excluding weekends) between start and end date
     */
    private List<String> generateTradingDates(LocalDate start, LocalDate end) {
        List<String> dates = new ArrayList<>();
        LocalDate current = start;
        
        while (!current.isAfter(end)) {
            // Exclude Saturday (6) and Sunday (7)
            if (current.getDayOfWeek().getValue() < 6) {
                dates.add(current.format(DATE_FORMATTER));
            }
            current = current.plusDays(1);
        }
        
        return dates;
    }
    
    /**
     * Fetch ticker tide and OHLC data for a specific date (used in structured concurrency)
     */
    private DateFetchResult fetchDataForDate(String ticker, String dateStr) {
        List<UnusualWhalesTickerTideDto.TickerTideItem> tickerTideData = new ArrayList<>();
        List<UnusualWhalesTickerOhlcDto.TickerOhlcItem> tickerOhlcData = new ArrayList<>();
        
        var tickerTideResponse = unusualWhalesClient.getTickerTide(ticker, dateStr);
        var tickerOhlcResponse = unusualWhalesClient.getTickerOhlc(ticker, dateStr, "1m");
        
        if (tickerTideResponse != null && tickerTideResponse.data() != null) {
            tickerTideData.addAll(tickerTideResponse.data());
        }
        if (tickerOhlcResponse != null && tickerOhlcResponse.data() != null) {
            tickerOhlcData.addAll(tickerOhlcResponse.data());
        }
        
        log.debug("Fetched {} tide points and {} OHLC points for {} on {}", 
            tickerTideData.size(), tickerOhlcData.size(), ticker, dateStr);
        
        return new DateFetchResult(tickerTideData, tickerOhlcData);
    }
    
    /**
     * Merge ticker tide data with ticker OHLC prices and calculate cumulative values
     */
    private List<TickerTideDataPoint> mergeAndTransformData(
            String ticker,
            List<UnusualWhalesTickerTideDto.TickerTideItem> tickerTideData,
            List<UnusualWhalesTickerOhlcDto.TickerOhlcItem> tickerOhlcData) {
        
        // Create price map with timestamp matching
        Map<Long, BigDecimal> priceMap = new HashMap<>();
        for (var item : tickerOhlcData) {
            try {
                long timestamp = Instant.parse(item.startTime()).toEpochMilli();
                BigDecimal closePrice = item.close() != null ? new BigDecimal(item.close()) : null;
                if (closePrice != null) {
                    priceMap.put(timestamp, closePrice);
                    
                    // Also add rounded timestamp (to nearest minute) for better matching
                    long roundedTimestamp = (timestamp / 60000) * 60000;
                    priceMap.putIfAbsent(roundedTimestamp, closePrice);
                }
            } catch (Exception e) {
                log.debug("Failed to parse OHLC timestamp: {}", item.startTime());
            }
        }
        
        log.debug("Created {} price map with {} entries", ticker, priceMap.size());
        
        // Transform and accumulate ticker tide data
        List<TickerTideDataPoint> result = new ArrayList<>();
        BigDecimal cumulativeCallPremium = BigDecimal.ZERO;
        BigDecimal cumulativePutPremium = BigDecimal.ZERO;
        int cumulativeCallVolume = 0;
        int cumulativePutVolume = 0;
        
        for (var item : tickerTideData) {
            try {
                long timestamp = Instant.parse(item.tapeTime()).toEpochMilli();
                
                // Find closest price
                BigDecimal tickerClose = findClosestPrice(timestamp, priceMap);
                
                // Parse and accumulate current tick values
                BigDecimal currentCallPremium = item.netCallPremium() != null ? item.netCallPremium() : BigDecimal.ZERO;
                BigDecimal currentPutPremium = item.netPutPremium() != null ? item.netPutPremium() : BigDecimal.ZERO;
                int currentCallVolume = item.netCallVolume() != null ? item.netCallVolume() : 0;
                int currentPutVolume = item.netPutVolume() != null ? item.netPutVolume() : 0;
                
                // Accumulate values
                cumulativeCallPremium = cumulativeCallPremium.add(currentCallPremium);
                cumulativePutPremium = cumulativePutPremium.add(currentPutPremium);
                cumulativeCallVolume += currentCallVolume;
                cumulativePutVolume += currentPutVolume;
                
                // Calculate net volume and net delta
                int netVolume = cumulativeCallVolume + cumulativePutVolume;
                BigDecimal netDelta = cumulativeCallPremium.add(cumulativePutPremium);
                
                result.add(new TickerTideDataPoint(
                    timestamp,
                    item.date(),
                    cumulativeCallPremium,
                    cumulativePutPremium,
                    netVolume,
                    tickerClose,
                    item.tapeTime(),
                    netDelta,
                    cumulativeCallVolume,
                    cumulativePutVolume
                ));
                
            } catch (Exception e) {
                log.debug("Failed to parse ticker tide item: {}", item.tapeTime(), e);
            }
        }
        
        long withPrice = result.stream().filter(d -> d.tickerClose() != null).count();
        log.debug("Transformed {} data points, {} with {} prices", result.size(), withPrice, ticker);
        
        return result;
    }
    
    /**
     * Find closest price within ±2 minutes window
     */
    private BigDecimal findClosestPrice(long targetTimestamp, Map<Long, BigDecimal> priceMap) {
        // Try exact match first
        if (priceMap.containsKey(targetTimestamp)) {
            return priceMap.get(targetTimestamp);
        }
        
        // Try rounded timestamp
        long roundedTimestamp = (targetTimestamp / 60000) * 60000;
        if (priceMap.containsKey(roundedTimestamp)) {
            return priceMap.get(roundedTimestamp);
        }
        
        // Search within ±2 minutes window
        for (long offset = -120000; offset <= 120000; offset += 60000) {
            long testTimestamp = targetTimestamp + offset;
            if (priceMap.containsKey(testTimestamp)) {
                return priceMap.get(testTimestamp);
            }
        }
        
        return null; // No match found
    }
}
