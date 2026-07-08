package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.dto.UnusualWhalesTickerOhlcDto;
import com.marketmono.entity.StockPrice;
import com.marketmono.repository.StockPriceRepository;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for collecting and aggregating stock prices from the market data API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceCollectionService {

    private static final LocalTime DAILY_SUMMARY_TIME = LocalTime.MIDNIGHT;
    // Collection window: 9:30 AM - 4:15 PM ET, Monday-Friday
    private static final LocalTime COLLECTION_WINDOW_OPEN = LocalTime.of(9, 30);
    private static final LocalTime COLLECTION_WINDOW_CLOSE = LocalTime.of(16, 15);
    // Regular trading session used for daily OHLC summaries (official close at 16:00)
    private static final LocalTime REGULAR_SESSION_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_SESSION_CLOSE = LocalTime.of(16, 0);
    
    @Value("${market-data.stock-prices.intraday-retention-days:30}")
    private int intradayRetentionDays;

    @Value("${market-data.stock-prices.daily-retention-days:1825}")
    private int dailyRetentionDays;
    
    @Value("${market-data.stock-prices.max-concurrent-requests}")
    private int maxConcurrentRequests;

    @Value("${market-data.stock-prices.dashboard-tickers:SPY,QQQ,IWM,DIA}")
    private String dashboardTickersCsv;
    
    private final UnusualWhalesClient uwClient;
    private final StockPriceRepository stockPriceRepository;
    
    /**
     * Get fixed dashboard tickers for deterministic collection coverage.
     */
    private List<String> getDashboardTickers() {
        return java.util.Arrays.stream(dashboardTickersCsv.split(","))
            .map(String::trim)
            .filter(ticker -> !ticker.isBlank())
            .map(String::toUpperCase)
            .distinct()
            .toList();
    }
    
    /**
     * Real-time collection - called by scheduler during market hours
     * Collects current stock prices for active tickers
     */
    public void collectCurrentPrices() {
        ZonedDateTime marketTime = ZonedDateTime.now(DateUtils.EASTERN_TIME);
        if (!isWithinCollectionWindow(marketTime)) {
            log.debug("Skipping stock price collection outside market window (now={} ET)", marketTime.toLocalTime());
            return;
        }

        List<String> activeTickers = getDashboardTickers();
        
        if (activeTickers.isEmpty()) {
            log.debug("No dashboard tickers configured for price collection");
            return;
        }
        
        // Limit the number of tickers to prevent overwhelming the system
        int maxTickers = Math.min(activeTickers.size(), maxConcurrentRequests * 10); // Process up to 10x concurrent limit
        List<String> tickersToProcess = activeTickers.subList(0, maxTickers);
        
        log.info("Starting stock price collection for {} fixed dashboard tickers", tickersToProcess.size());
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        // Use semaphore to limit concurrent API operations
        Semaphore semaphore = new Semaphore(maxConcurrentRequests);
        
        // Thread-safe list for concurrent addition from virtual threads (avoids synchronized/pinning)
        List<StockPrice> pricesToSave = Collections.synchronizedList(new java.util.ArrayList<>());
        
        // Use parallel processing for API calls only
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = tickersToProcess.stream()
            .map(ticker -> CompletableFuture.runAsync(() -> {
                try {
                    // Acquire semaphore permit for API call
                    semaphore.acquire();
                    
                    StockStateResponse state = uwClient.getStockState(ticker);
                    
                    if (state != null && state.data() != null && state.data().close() != null) {
                        StockPrice price = StockPrice.builder()
                            .ticker(ticker)
                            .priceDate(DateUtils.getCurrentDateInEasternTime())
                            .priceTime(LocalTime.now(DateUtils.EASTERN_TIME).truncatedTo(ChronoUnit.MINUTES))
                            .close(new BigDecimal(state.data().close()))
                            .volume(state.data().volume())
                            .source("MARKET_DATA")
                            .build();
                        
                        // Add to thread-safe collection
                        pricesToSave.add(price);
                        successCount.incrementAndGet();
                    } else {
                        log.warn("Null response for ticker {}", ticker);
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    log.error("Interrupted while collecting price for {}: {}", ticker, e.getMessage());
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to collect price for {}: {}", ticker, e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    // Always release the semaphore
                    semaphore.release();
                }
            }, executor))
            .toList();
        
            // Wait for all API calls to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        
        // Now save all collected prices to database in batches to avoid connection pool exhaustion
        if (!pricesToSave.isEmpty()) {
            try {
                // Save in batches of 50 to balance performance and connection usage
                int batchSize = 50;
                for (int i = 0; i < pricesToSave.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, pricesToSave.size());
                    List<StockPrice> batch = pricesToSave.subList(i, endIndex);
                    stockPriceRepository.saveAll(batch);
                    log.debug("Saved batch of {} stock prices", batch.size());
                }
            } catch (Exception e) {
                log.error("Failed to save stock prices batch: {}", e.getMessage());
            }
        }
        
        log.info("[MARKET_DATA] {} fetched - {} synced", tickersToProcess.size(), successCount.get());
        if (failCount.get() > 0) {
            log.warn("Failed to collect prices for {} tickers", failCount.get());
        }
    }
    
    /**
     * End-of-day aggregation - calculate OHLC from 1-minute samples
     * Called by scheduler at 4:05 PM EST Monday-Friday
     */
    @Transactional
    public void aggregateDailyPrices() {
        LocalDate today = DateUtils.getCurrentDateInEasternTime();
        List<String> tickers = stockPriceRepository.findDistinctTickersByDate(today);
        
        if (tickers.isEmpty()) {
            log.info("No tickers found for daily aggregation on {}", today);
            return;
        }
        
        log.info("Starting daily OHLC aggregation for {} tickers", tickers.size());
        
        int aggregatedCount = 0;
        
        for (String ticker : tickers) {
            try {
                // Restrict to the regular session (9:30-16:00 ET) so the daily close matches
                // the official close instead of a 16:00-16:15 post-market sample.
                List<StockPrice> minutePrices = stockPriceRepository
                    .findIntradayPricesByTickerAndDateOrderByTime(ticker, today, DAILY_SUMMARY_TIME)
                    .stream()
                    .filter(price -> !price.getPriceTime().isBefore(REGULAR_SESSION_OPEN)
                        && !price.getPriceTime().isAfter(REGULAR_SESSION_CLOSE))
                    .toList();
                
                if (minutePrices.isEmpty()) {
                    continue;
                }
                
                // Calculate OHLC from minute samples
                BigDecimal open = minutePrices.get(0).getClose();
                BigDecimal high = minutePrices.stream()
                    .map(StockPrice::getClose)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
                BigDecimal low = minutePrices.stream()
                    .map(StockPrice::getClose)
                    .filter(price -> price.compareTo(BigDecimal.ZERO) > 0) // Exclude zero prices
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
                BigDecimal close = minutePrices.get(minutePrices.size() - 1).getClose();
                Long totalVolume = minutePrices.get(minutePrices.size() - 1).getVolume();
                
                List<StockPrice> existingSummaryRows = stockPriceRepository
                    .findDailySummaryPricesByTickerAndDateRange(ticker, today, today, DAILY_SUMMARY_TIME);

                StockPrice dailyPrice = existingSummaryRows.isEmpty()
                    ? StockPrice.builder()
                        .ticker(ticker)
                        .priceDate(today)
                        .priceTime(DAILY_SUMMARY_TIME)
                        .source("MARKET_DATA_DAILY")
                        .build()
                    : existingSummaryRows.get(0);

                dailyPrice.setOpen(open);
                dailyPrice.setHigh(high);
                dailyPrice.setLow(low);
                dailyPrice.setClose(close);
                dailyPrice.setVolume(totalVolume);
                
                stockPriceRepository.save(dailyPrice);
                aggregatedCount++;
                
            } catch (Exception e) {
                log.error("Failed to aggregate daily prices for {}: {}", ticker, e.getMessage());
            }
        }
        
        log.info("Daily OHLC aggregation complete: {} tickers processed", aggregatedCount);
    }
    
    /**
     * Cleanup old data - called by scheduler daily at 2:00 AM EST
     * Deletes stock prices older than retention period
     */
    @Transactional
    public void cleanupOldPrices() {
        LocalDate intradayCutoffDate = DateUtils.getCurrentDateInEasternTime().minusDays(this.intradayRetentionDays);
        LocalDate dailySummaryCutoffDate = DateUtils.getCurrentDateInEasternTime().minusDays(this.dailyRetentionDays);
        
        try {
            int deletedIntraday = stockPriceRepository.deleteIntradayPricesBefore(
                intradayCutoffDate,
                DAILY_SUMMARY_TIME
            );
            int deletedDailySummaries = stockPriceRepository.deleteDailySummaryPricesBefore(
                dailySummaryCutoffDate,
                DAILY_SUMMARY_TIME
            );

            if (deletedIntraday > 0 || deletedDailySummaries > 0) {
                log.info(
                    "Cleaned up {} intraday rows before {} and {} daily summary rows before {}",
                    deletedIntraday,
                    intradayCutoffDate,
                    deletedDailySummaries,
                    dailySummaryCutoffDate
                );
            } else {
                log.debug(
                    "No stock price rows to clean up before intraday={} dailySummary={}",
                    intradayCutoffDate,
                    dailySummaryCutoffDate
                );
            }
        } catch (Exception e) {
            log.error("Failed to cleanup old stock prices: {}", e.getMessage());
        }
    }
    
    /**
     * Collect historical OHLC data from the market data API.
     * Replaces Polygon /v2/aggs/ticker/{ticker}/range endpoint
     * 
     * @param ticker Stock ticker symbol
     * @param date Date for which to collect OHLC data (YYYY-MM-DD format)
     * @param interval Candle size (1m, 5m, 1h, 1d)
     */
    @Transactional
    public void collectHistoricalOhlc(String ticker, String date, String interval) {
        log.debug("Collecting historical OHLC data for {} on {} with {} interval", ticker, date, interval);
        
        try {
            UnusualWhalesTickerOhlcDto response = uwClient.getTickerOhlc(ticker, date, interval);
            
            if (response == null || response.data() == null || response.data().isEmpty()) {
                log.warn("No OHLC data available for {} on {} with {} interval", ticker, date, interval);
                return;
            }
            
            LocalDate priceDate = LocalDate.parse(date);
            List<StockPrice> prices = response.data().stream()
                .map(candle -> {
                    try {
                        return StockPrice.builder()
                            .ticker(ticker)
                            .priceDate(priceDate)
                            .priceTime(candle.startTime() != null ? 
                                LocalTime.parse(candle.startTime().substring(11, 19)) : 
                                LocalTime.MIDNIGHT)
                            .open(new BigDecimal(candle.open()))
                            .high(new BigDecimal(candle.high()))
                            .low(new BigDecimal(candle.low()))
                            .close(new BigDecimal(candle.close()))
                            .volume(candle.volume())
                            .source("MARKET_DATA_OHLC")
                            .build();
                    } catch (Exception _) {
                        log.error("Error parsing candle for {} on {}", ticker, date);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
            
            if (!prices.isEmpty()) {
                stockPriceRepository.saveAll(prices);
                log.info("Saved {} OHLC candles for {} on {}", prices.size(), ticker, date);
            }
        } catch (Exception e) {
            log.error("Failed to collect historical OHLC for {} on {}: {}", ticker, date, e.getMessage());
        }
    }

    /**
     * Whether the given market time is inside the collection window
     * (Monday-Friday, 9:30 AM - 4:15 PM ET).
     */
    boolean isWithinCollectionWindow(ZonedDateTime marketTime) {
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = marketTime.toLocalTime();
        return !time.isBefore(COLLECTION_WINDOW_OPEN) && !time.isAfter(COLLECTION_WINDOW_CLOSE);
    }
}
