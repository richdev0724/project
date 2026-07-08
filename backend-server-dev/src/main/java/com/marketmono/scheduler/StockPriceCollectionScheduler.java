package com.marketmono.scheduler;

import com.marketmono.service.StockPriceCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for stock price collection operations.
 * Conditionally enabled based on stock price collection configuration.
 */
@Component
@ConditionalOnProperty(
    prefix = "market-data.stock-prices",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class StockPriceCollectionScheduler {

    private static final String MARKET_TIMEZONE = "America/New_York";

    private final StockPriceCollectionService service;

    /**
     * Real-time collection - runs every minute Mon-Fri; the service further gates
     * collection to the 9:30 AM - 4:15 PM ET market window.
     */
    @Scheduled(cron = "${market-data.stock-prices.collection-schedule}", zone = MARKET_TIMEZONE)
    @SchedulerLock(name = "stockPrices_collectCurrent", lockAtMostFor = "PT5M", lockAtLeastFor = "PT50S")
    public void collectCurrentPrices() {
        try {
            service.collectCurrentPrices();
        } catch (Exception e) {
            log.error("Error during scheduled stock price collection", e);
        }
    }

    /**
     * End-of-day aggregation - calculate OHLC from 1-minute samples
     * Runs at 4:20 PM ET Monday-Friday, after the 4:15 PM collection cutoff
     */
    @Scheduled(cron = "${market-data.stock-prices.aggregation-schedule}", zone = MARKET_TIMEZONE)
    @SchedulerLock(name = "stockPrices_aggregateDaily", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void aggregateDailyPrices() {
        try {
            service.aggregateDailyPrices();
        } catch (Exception e) {
            log.error("Error during scheduled stock price aggregation", e);
        }
    }

    /**
     * Cleanup old data - runs daily at 2:00 AM ET
     * Deletes stock prices older than retention period
     */
    @Scheduled(cron = "${market-data.stock-prices.cleanup-schedule}", zone = MARKET_TIMEZONE)
    @SchedulerLock(name = "stockPrices_cleanupOld", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void cleanupOldPrices() {
        try {
            service.cleanupOldPrices();
        } catch (Exception e) {
            log.error("Error during scheduled stock price cleanup", e);
        }
    }
}