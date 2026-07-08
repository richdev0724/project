package com.marketmono.scheduler;

import com.marketmono.service.StrataStockPriceCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "market-data.strata-stock-prices", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class StrataStockPriceCollectionScheduler {

    private final StrataStockPriceCollectionService collectionService;

    @Scheduled(
        fixedRateString = "${market-data.strata-stock-prices.interval-ms:60000}",
        initialDelayString = "${market-data.strata-stock-prices.initial-delay-ms:10000}"
    )
    @SchedulerLock(name = "strataStockPrice_collectLatest", lockAtMostFor = "PT2M", lockAtLeastFor = "PT50S")
    public void collectLatestPrices() {
        log.debug("[STRATA-STOCK-PRICE] Starting scheduled collection");
        collectionService.collectLatestPrices();
    }
}
