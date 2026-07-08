package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.GreekExposureByExpiryDto;
import com.marketmono.dto.GreekExposureByStrikeDto;
import com.marketmono.dto.GreekExposureResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.metrics.UwCallSource;
import com.marketmono.metrics.UwCallSourceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for fetching greek exposure (gamma, delta, charm, vanna) data from upstream market data API
 * Cached with 5-minute TTL for market data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GammaExposureService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get gamma exposure aggregated by strike price for a ticker
     * Used by frontend for strike-level gamma exposure charts
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date (optional, defaults to most recent trading day)
     * @return Greek exposure data grouped by strike price
     */
    @Cacheable(value = "gammaExposureCache", key = "'strike-' + #ticker + '-' + #date", cacheManager = "gammaExposureCacheManager")
    public List<GreekExposureByStrikeDto> getGammaExposureByStrike(String ticker, String date) {
        log.debug("Fetching gamma exposure by strike for {}", ticker);
        
        try {
            GreekExposureResponseDto<GreekExposureByStrikeDto> response = UwCallSourceContext.callWith(
                UwCallSource.USER_REQUEST,
                () -> unusualWhalesClient.getGreekExposureByStrike(ticker.toUpperCase(), date)
            );
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} strike-level gamma exposure data points for {}", 
                    response.data().size(), ticker);
                return response.data();
            } else {
                log.warn("No gamma exposure by strike data returned for {}", ticker);
                return List.of();
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching gamma exposure by strike for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch gamma exposure by strike: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get gamma exposure aggregated by expiration date for a ticker
     * Used by frontend for expiration-level analysis and date selection
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date (optional, defaults to most recent trading day)
     * @return Greek exposure data grouped by expiration date
     */
    @Cacheable(value = "gammaExposureCache", key = "'expiry-' + #ticker + '-' + #date", cacheManager = "gammaExposureCacheManager")
    public List<GreekExposureByExpiryDto> getGammaExposureByExpiry(String ticker, String date) {
        log.debug("Fetching gamma exposure by expiry for {}", ticker);
        
        try {
            GreekExposureResponseDto<GreekExposureByExpiryDto> response = UwCallSourceContext.callWith(
                UwCallSource.USER_REQUEST,
                () -> unusualWhalesClient.getGreekExposureByExpiry(ticker.toUpperCase(), date)
            );
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} expiry-level gamma exposure data points for {}", 
                    response.data().size(), ticker);
                return response.data();
            } else {
                log.warn("No gamma exposure by expiry data returned for {}", ticker);
                return List.of();
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching gamma exposure by expiry for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch gamma exposure by expiry: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get list of available expiration dates for a ticker
     * Wrapper around getGammaExposureByExpiry that returns only the expiration dates
     * 
     * @param ticker Stock ticker symbol
     * @return List of expiration dates as strings (YYYY-MM-DD format)
     */
    public List<String> getExpirationDates(String ticker) {
        log.debug("Fetching expiration dates for {}", ticker);
        
        List<GreekExposureByExpiryDto> expiryData = getGammaExposureByExpiry(ticker, null);
        return expiryData.stream()
            .map(dto -> dto.expiry().toString())
            .sorted()
            .toList();
    }
}
