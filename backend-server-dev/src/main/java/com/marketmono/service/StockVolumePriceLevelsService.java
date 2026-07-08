package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OffLitPriceLevelDto;
import com.marketmono.dto.StockVolumePriceLevelsResponse;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.UnusualWhalesShedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for fetching stock volume price levels (dark pool & lit pool) from upstream market data API
 * Cached with 5-minute TTL for market data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockVolumePriceLevelsService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get stock volume price levels (dark & lit pool data) for a specific ticker
     * Returns aggregated volume by price level for both off-lit (dark pool) and lit (exchange) trades
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional date in YYYY-MM-DD format (defaults to current/last market day)
     * @return Volume price levels data
     */
    @Cacheable(value = "volumePriceLevelsCache", 
               key = "'vpl-' + #ticker + '-' + (#date ?: 'latest')", 
               cacheManager = "volumePriceLevelsCacheManager")
    public List<OffLitPriceLevelDto> getVolumePriceLevels(String ticker, String date) {
        log.debug("Fetching volume price levels for {}", ticker);
        
        try {
            StockVolumePriceLevelsResponse response = 
                unusualWhalesClient.getStockVolumePriceLevels(ticker.toUpperCase(), date);
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} volume price levels for {}", 
                    response.data().size(), ticker);
                return response.data();
            } else {
                log.warn("No volume price levels data returned for {}", ticker);
                return List.of();
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching volume price levels for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch volume price levels: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get latest volume price levels (no date specified)
     * 
     * @param ticker Stock ticker symbol
     * @return Latest volume price levels data
     */
    public List<OffLitPriceLevelDto> getLatestVolumePriceLevels(String ticker) {
        return getVolumePriceLevels(ticker, null);
    }
    
    /**
     * Calculate total dark pool (off-lit) volume across all price levels
     * 
     * @param data List of volume price levels
     * @return Total dark pool volume
     */
    public long calculateTotalDarkPoolVolume(List<OffLitPriceLevelDto> data) {
        return data.stream()
            .mapToLong(OffLitPriceLevelDto::offVol)
            .sum();
    }
    
    /**
     * Calculate total lit pool (exchange) volume across all price levels
     * 
     * @param data List of volume price levels
     * @return Total lit pool volume
     */
    public long calculateTotalLitVolume(List<OffLitPriceLevelDto> data) {
        return data.stream()
            .mapToLong(OffLitPriceLevelDto::litVol)
            .sum();
    }
    
    /**
     * Calculate dark pool percentage of total volume
     * 
     * @param data List of volume price levels
     * @return Dark pool percentage (0-100)
     */
    public double calculateDarkPoolPercentage(List<OffLitPriceLevelDto> data) {
        long darkPoolVol = calculateTotalDarkPoolVolume(data);
        long litVol = calculateTotalLitVolume(data);
        long totalVol = darkPoolVol + litVol;
        
        return totalVol > 0 ? (darkPoolVol * 100.0) / totalVol : 0.0;
    }
}



