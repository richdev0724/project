package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.RealizedVolatilityDto;
import com.marketmono.dto.RealizedVolatilityResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.UnusualWhalesShedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for fetching Realized Volatility data from upstream market data API
 * Compares implied volatility (IV) with realized volatility (RV)
 * Cached with 5-minute TTL for market data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealizedVolatilityService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get Realized Volatility data for a ticker over a time period
     * 
     * @param ticker Stock ticker symbol
     * @param date Trading date (optional, defaults to last trading date)
     * @param timeframe Optional timeframe ('YTD', '1D', '1W', '1M', '1Y', etc., default: '1Y')
     * @return Realized volatility data points with IV vs RV comparison
     */
    @Cacheable(value = "realizedVolatilityCache", 
               key = "'realized-volatility-' + #ticker + '-' + #date + '-' + #timeframe", 
               cacheManager = "realizedVolatilityCacheManager")
    public List<RealizedVolatilityDto> getRealizedVolatility(String ticker, String date, String timeframe) {
        log.debug("Fetching realized volatility for {}", ticker);
        
        try {
            RealizedVolatilityResponseDto response = 
                unusualWhalesClient.getRealizedVolatility(ticker.toUpperCase(), date, timeframe);
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} realized volatility data points for {}", 
                    response.data().size(), ticker);
                return response.data();
            } else {
                log.warn("No realized volatility data returned for {}", ticker);
                return List.of();
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching realized volatility for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch realized volatility: " + e.getMessage(), e);
        }
    }
}
