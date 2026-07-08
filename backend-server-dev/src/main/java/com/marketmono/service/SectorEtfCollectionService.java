package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.UnusualWhalesTickerOhlcDto;
import com.marketmono.entity.SectorEtfPrice;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled service to collect sector ETF prices hourly from upstream market data API
 * Reduces frontend API load from 3,300 calls/day to 792 background calls/day
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SectorEtfCollectionService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    private final SectorEtfPricePersistenceService sectorEtfPricePersistenceService;
    
    private static final List<String> SECTOR_ETFS = List.of(
        "XLC", "XLY", "XLP", "XLE", "XLF", "XLV",
        "XLI", "XLK", "XLB", "XLRE", "XLU"
    );
    
    private static final long DELAY_BETWEEN_CALLS_MS = 2000; // 2 seconds to avoid circuit breaker
    
    /**
     * Collect sector ETF prices hourly (runs at minute 5 of every hour)
     * Sequential API calls with delays to prevent circuit breaker trips
     */
    @Scheduled(cron = "0 5 * * * *") // Every hour at minute 5
    @SchedulerLock(name = "sectorEtf_collectHourlyPrices", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void collectHourlyPrices() {
        log.info("Starting hourly sector ETF price collection for {} tickers", SECTOR_ETFS.size());
        
        LocalDate today = DateUtils.getCurrentDateInEasternTime();
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        
        for (String ticker : SECTOR_ETFS) {
            try {
                // Skip if we already have today's price
                if (sectorEtfPricePersistenceService.existsForDate(ticker, today)) {
                    log.debug("[{}] Price already exists for {}, skipping", ticker, today);
                    skipCount++;
                    continue;
                }
                
                // Fetch today's OHLC data from the upstream market data API.
                UnusualWhalesTickerOhlcDto ohlcData = unusualWhalesClient.getTickerOhlc(
                    ticker, today.toString(), "1d"
                );
                
                if (ohlcData != null && ohlcData.data() != null && !ohlcData.data().isEmpty()) {
                    String closePriceStr = ohlcData.data().get(0).close();
                    BigDecimal closePrice = closePriceStr != null ? new BigDecimal(closePriceStr) : null;
                    
                    if (closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0) {
                        // Save to database
                        SectorEtfPrice priceEntity = SectorEtfPrice.builder()
                            .ticker(ticker)
                            .date(today)
                            .closePrice(closePrice)
                            .build();
                        
                        sectorEtfPricePersistenceService.save(priceEntity);
                        successCount++;
                        log.debug("[{}] Saved price: ${} for {}", ticker, closePrice, today);
                    } else {
                        log.warn("[{}] Invalid close price: {}", ticker, closePrice);
                        errorCount++;
                    }
                } else {
                    log.warn("[{}] No OHLC data received from upstream market data API", ticker);
                    errorCount++;
                }
                
                // Delay between API calls to avoid circuit breaker
                if (SECTOR_ETFS.indexOf(ticker) < SECTOR_ETFS.size() - 1) {
                    Thread.sleep(DELAY_BETWEEN_CALLS_MS);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[{}] Collection interrupted", ticker, e);
                break;
            } catch (Exception e) {
                log.error("[{}] Error collecting price", ticker, e);
                errorCount++;
            }
        }
        
        log.info("Hourly collection complete - Success: {}, Skipped: {}, Errors: {} (Total API calls: {})",
            successCount, skipCount, errorCount, successCount + errorCount);
    }
    
    /**
     * Backfill historical prices for a specific date
     * Manual trigger via controller endpoint for historical data population
     */
    public void backfillDate(LocalDate date) {
        log.info("Starting backfill for date: {}", date);
        
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        
        for (String ticker : SECTOR_ETFS) {
            try {
                if (sectorEtfPricePersistenceService.existsForDate(ticker, date)) {
                    log.debug("[{}] Price already exists for {}", ticker, date);
                    skipCount++;
                    continue;
                }
                
                log.debug("[{}] Fetching OHLC for {} (API: /api/stock/{}/ohlc/1d?date={})", 
                    ticker, date, ticker, date);
                UnusualWhalesTickerOhlcDto ohlcData = unusualWhalesClient.getTickerOhlc(
                    ticker, date.toString(), "1d"
                );
                
                if (ohlcData == null) {
                    log.warn("[{}] Received null response from ticker-ohlc for {} - API may have returned error", 
                        ticker, date);
                    errorCount++;
                    Thread.sleep(DELAY_BETWEEN_CALLS_MS);
                    continue;
                }
                
                if (ohlcData.data() == null || ohlcData.data().isEmpty()) {
                    log.warn("[{}] Empty data array from ticker-ohlc for {}", 
                        ticker, date);
                    errorCount++;
                    continue;
                }
                
                String closePriceStr = ohlcData.data().get(0).close();
                BigDecimal closePrice = closePriceStr != null ? new BigDecimal(closePriceStr) : null;
                
                if (closePrice == null) {
                    log.warn("[{}] Null close price for {}", ticker, date);
                    errorCount++;
                } else if (closePrice.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("[{}] Invalid close price {} for {}", ticker, closePrice, date);
                    errorCount++;
                } else {
                    SectorEtfPrice priceEntity = SectorEtfPrice.builder()
                        .ticker(ticker)
                        .date(date)
                        .closePrice(closePrice)
                        .build();
                    
                    sectorEtfPricePersistenceService.save(priceEntity);
                    log.info("[{}] Saved price ${} for {}", ticker, closePrice, date);
                    successCount++;
                }
                
                Thread.sleep(DELAY_BETWEEN_CALLS_MS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Backfill interrupted for {}", date, e);
                break;
            } catch (Exception e) {
                log.error("[{}] Error backfilling for {}", ticker, date, e);
                errorCount++;
            }
        }
        
        log.info("Backfill complete for {} - Success: {}, Skipped: {}, Errors: {}", 
            date, successCount, skipCount, errorCount);
    }
}
