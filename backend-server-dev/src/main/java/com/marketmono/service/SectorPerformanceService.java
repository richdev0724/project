package com.marketmono.service;

import com.marketmono.dto.SectorPerformanceResponse;
import com.marketmono.entity.SectorEtfPrice;
import com.marketmono.repository.SectorEtfPriceRepository;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SectorPerformanceService {
    
    private final SectorEtfPriceRepository sectorEtfPriceRepository;
    
    // 11 sector ETF tickers with their sector names
    private static final Map<String, String> SECTOR_ETFS = Map.ofEntries(
        Map.entry("XLC", "Communication Services"),
        Map.entry("XLY", "Consumer Discretionary"),
        Map.entry("XLP", "Consumer Staples"),
        Map.entry("XLE", "Energy"),
        Map.entry("XLF", "Financials"),
        Map.entry("XLV", "Healthcare"),
        Map.entry("XLI", "Industrials"),
        Map.entry("XLK", "Information Technology"),
        Map.entry("XLB", "Materials"),
        Map.entry("XLRE", "Real Estate"),
        Map.entry("XLU", "Utilities")
    );
    
    /**
     * Get sector performance data with 5-minute cache TTL
     * Queries pre-populated PostgreSQL database (no upstream market data API calls per request)
     * Database populated hourly by SectorEtfCollectionService (33 API calls/hour)
     */
    @Cacheable(value = "sectorPerformance", key = "'all'", cacheManager = "sectorPerformanceCacheManager")
    public List<SectorPerformanceResponse> getSectorPerformance() {
        return getSectorPerformanceUncached();
    }

    public List<SectorPerformanceResponse> getSectorPerformanceUncached() {
        log.info("Fetching sector performance from database (cached for 5 minutes)");
        
        try {
            LocalDate today = DateUtils.getCurrentDateInEasternTime();
            LocalDate thirtyDaysAgo = today.minusDays(30);
            LocalDate sixMonthsAgo = today.minusMonths(6);
            
            List<SectorPerformanceResponse> results = new ArrayList<>();
            
            for (Map.Entry<String, String> entry : SECTOR_ETFS.entrySet()) {
                String ticker = entry.getKey();
                String sectorName = entry.getValue();
                
                try {
                    SectorPerformanceResponse performance = calculateSectorPerformanceFromDb(
                        ticker, sectorName, today, thirtyDaysAgo, sixMonthsAgo
                    );
                    results.add(performance);
                } catch (Exception e) {
                    log.warn("Failed to calculate performance for ticker {}, using fallback", ticker, e);
                    results.add(createFallbackResponse(ticker, sectorName));
                }
            }
            
            log.info("Successfully retrieved performance for {} sectors from database", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Error fetching sector performance from database, returning fallback data", e);
            return createAllFallbackData();
        }
    }
    
    private SectorPerformanceResponse calculateSectorPerformanceFromDb(
        String ticker,
        String sectorName,
        LocalDate today,
        LocalDate thirtyDaysAgo,
        LocalDate sixMonthsAgo
    ) {
        List<String> allTickers = new ArrayList<>(SECTOR_ETFS.keySet());
        
        // BATCH fetch ALL latest prices before today (1 query vs 11)
        List<SectorEtfPrice> currentPrices = sectorEtfPriceRepository
            .findLatestPricesBeforeDateBatch(allTickers, today);
        
        // Extract current for this ticker
        Optional<SectorEtfPrice> currentPriceOpt = currentPrices.stream()
            .filter(p -> p.getTicker().equals(ticker))
            .findFirst();
            
        if (currentPriceOpt.isEmpty()) {
            log.warn("[{}] No price data in database", ticker);
            return createFallbackResponse(ticker, sectorName);
        }
        
        SectorEtfPrice currentPriceEntity = currentPriceOpt.get();
        LocalDate latestDate = currentPriceEntity.getDate();
        BigDecimal currentPrice = currentPriceEntity.getClosePrice();
        
        // BATCH yesterday (1 query)
        List<SectorEtfPrice> yesterdayPrices = sectorEtfPriceRepository
            .findLatestPricesBeforeDateBatch(allTickers, latestDate.minusDays(1));
        Optional<SectorEtfPrice> yesterdayPriceOpt = yesterdayPrices.stream()
            .filter(p -> p.getTicker().equals(ticker)).findFirst();
        BigDecimal yesterdayPriceValue = yesterdayPriceOpt.map(SectorEtfPrice::getClosePrice)
            .orElse(currentPrice);
        
        // BATCH 30d ago (1 query)
        List<SectorEtfPrice> monthPrices = sectorEtfPriceRepository
            .findLatestPricesBeforeDateBatch(allTickers, thirtyDaysAgo);
        Optional<SectorEtfPrice> monthPriceOpt = monthPrices.stream()
            .filter(p -> p.getTicker().equals(ticker)).findFirst();
        BigDecimal monthAgoPrice = monthPriceOpt.map(SectorEtfPrice::getClosePrice)
            .orElse(currentPrice);
        
        // BATCH 6m ago (1 query)
        List<SectorEtfPrice> sixMonthPrices = sectorEtfPriceRepository
            .findLatestPricesBeforeDateBatch(allTickers, sixMonthsAgo);
        Optional<SectorEtfPrice> sixMonthPriceOpt = sixMonthPrices.stream()
            .filter(p -> p.getTicker().equals(ticker)).findFirst();
        BigDecimal sixMonthAgoPrice = sixMonthPriceOpt.map(SectorEtfPrice::getClosePrice)
            .orElse(currentPrice);
        
        // Calculate price changes
        BigDecimal dailyChange = currentPrice.subtract(yesterdayPriceValue);
        BigDecimal monthlyChange = currentPrice.subtract(monthAgoPrice);
        BigDecimal sixMonthChange = currentPrice.subtract(sixMonthAgoPrice);
        
        // Calculate percentage changes
        double dailyPercent = calculatePercentChange(yesterdayPriceValue, currentPrice);
        double monthlyPercent = calculatePercentChange(monthAgoPrice, currentPrice);
        double sixMonthPercent = calculatePercentChange(sixMonthAgoPrice, currentPrice);
        
        log.debug("[{}] Latest: {}, Price: ${}, 1d: {}%", ticker, latestDate, currentPrice, dailyPercent);
        
        return new SectorPerformanceResponse(
            ticker, sectorName,
            new SectorPerformanceResponse.PriceChanges(dailyChange.doubleValue(), monthlyChange.doubleValue(), sixMonthChange.doubleValue()),
            new SectorPerformanceResponse.PercentageChanges(dailyPercent, monthlyPercent, sixMonthPercent),
            currentPrice.doubleValue()
        );
    }
    
    private double calculatePercentChange(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        BigDecimal change = newPrice.subtract(oldPrice);
        BigDecimal percent = change.divide(oldPrice, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        return percent.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    
    private SectorPerformanceResponse createFallbackResponse(String ticker, String sectorName) {
        return new SectorPerformanceResponse(
            ticker,
            sectorName,
            new SectorPerformanceResponse.PriceChanges(0.0, 0.0, 0.0),
            new SectorPerformanceResponse.PercentageChanges(0.0, 0.0, 0.0),
            0.0
        );
    }
    
    private List<SectorPerformanceResponse> createAllFallbackData() {
        return SECTOR_ETFS.entrySet().stream()
            .map(entry -> createFallbackResponse(entry.getKey(), entry.getValue()))
            .toList();
    }
}
