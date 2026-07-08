package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.IvRankDto;
import com.marketmono.dto.IvRankResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.UnusualWhalesShedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for fetching IV Rank data from upstream market data API
 * IV rank is a measure of where current implied volatility stands relative to its historical range
 * Cached with 5-minute TTL for market data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IvRankService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get IV Rank data for a ticker over a time period
     * 
     * @param ticker Stock ticker symbol
     * @param date Trading date (optional, defaults to last trading date)
     * @param timespan Optional timespan ('1y', '6m', '3m', '1m', '1w' or ISO date)
     * @return IV rank data points
     */
    @Cacheable(value = "ivRankCache", 
               key = "'iv-rank-' + #ticker + '-' + #date + '-' + #timespan", 
               cacheManager = "ivRankCacheManager")
    public List<IvRankDto> getIvRank(String ticker, String date, String timespan) {
        log.debug("Fetching IV rank for {}", ticker);
        
        try {
            IvRankResponseDto response = 
                unusualWhalesClient.getIvRank(ticker.toUpperCase(), date, timespan);
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} IV rank data points for {}", 
                    response.data().size(), ticker);
                return response.data();
            } else {
                log.warn("No IV rank data returned for {}", ticker);
                return List.of();
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching IV rank for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch IV rank: " + e.getMessage(), e);
        }
    }
}
