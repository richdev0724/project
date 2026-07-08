package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.GreekExposureByStrikeExpiryDto;
import com.marketmono.dto.GreekExposureResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.metrics.UwCallSource;
import com.marketmono.metrics.UwCallSourceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for fetching greek exposure by strike and expiry from upstream market data API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GreekExposureByStrikeExpiryService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get greek exposure data aggregated by strike for a specific expiry
     * 
     * @param ticker Stock ticker symbol
     * @param expiry Expiration date (required, format: YYYY-MM-DD)
     * @param date Market date (optional, defaults to most recent trading day)
     * @return List of greek exposure data points by strike for the expiry
     */
    @Cacheable(
        value = "gammaExposureCache",
        key = "'strike-expiry-' + #ticker + '-' + #expiry + '-' + (#date ?: 'latest')",
        cacheManager = "gammaExposureCacheManager"
    )
    public List<GreekExposureByStrikeExpiryDto> getGreekExposureByStrikeExpiry(
            String ticker, 
            String expiry, 
            String date) {
        
        log.info("Fetching greek exposure by strike-expiry for {} - expiry: {}, date: {}", 
                 ticker, expiry, date);
        
        try {
            GreekExposureResponseDto<GreekExposureByStrikeExpiryDto> response = UwCallSourceContext.callWith(
                UwCallSource.USER_REQUEST,
                () -> unusualWhalesClient.getGreekExposureByStrikeExpiry(ticker.toUpperCase(), expiry, date)
            );
            
            if (response == null || response.data() == null) {
                log.warn("No greek exposure data available for {} (expiry: {})", ticker, expiry);
                return Collections.emptyList();
            }
            
            log.info("Successfully fetched {} greek exposure by strike-expiry data points for {} (expiry: {})", 
                     response.data().size(), ticker, expiry);
            return response.data();
            
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching greek exposure by strike-expiry for {} (expiry: {})", ticker, expiry, e);
            throw new MarketDataProviderException("Failed to fetch greek exposure by strike-expiry: " + e.getMessage(), e);
        }
    }
}
