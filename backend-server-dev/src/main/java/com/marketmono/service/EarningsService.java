package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.EarningsByDateResponseDto;
import com.marketmono.dto.EarningsDetailedItemDto;
import com.marketmono.dto.EarningsDetailedResponseDto;
import com.marketmono.dto.EarningsItemDto;
import com.marketmono.dto.TickerEarningsResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for fetching earnings data from upstream market data API
 * Cached with 1-minute TTL for real-time earnings updates
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EarningsService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    // Self-injection for AOP proxy access (setter injection to avoid circular dependency)
    private EarningsService self;
    
    @Autowired
    @Lazy
    void setSelf(EarningsService self) { this.self = self; }
    
    private EarningsService proxy() { return (self != null) ? self : this; }    /**
     * Fetch premarket earnings for a specific date
     *
     * @param date Date in YYYY-MM-DD format
     * @return List of earnings items for premarket
     */
    @Cacheable(value = "earningsCache", key = "'premarket-' + #date", cacheManager = "earningsCacheManager")
    public List<EarningsItemDto> getPremarketEarnings(String date) {
        log.debug("Fetching premarket earnings for date: {}", date);

        try {
            EarningsDetailedResponseDto response = unusualWhalesClient.getPremarketEarnings(date, null, null);

            if (response != null && response.data() != null) {
                log.info("Retrieved {} premarket earnings for {}", response.data().size(), date);
                // Convert detailed items to simple items for backward compatibility
                return response.data().stream()
                    .map(item -> new EarningsItemDto(item.symbol(), item.fullName(), item.marketcap(), item.reportTime()))
                    .toList();
            } else {
                log.warn("No premarket earnings returned from API for {}", date);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching premarket earnings for date: {}", date, e);
            throw new MarketDataProviderException("Failed to fetch premarket earnings: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch afterhours earnings for a specific date
     *
     * @param date Date in YYYY-MM-DD format
     * @return List of earnings items for afterhours
     */
    @Cacheable(value = "earningsCache", key = "'afterhours-' + #date", cacheManager = "earningsCacheManager")
    public List<EarningsItemDto> getAfterhoursEarnings(String date) {
        log.debug("Fetching afterhours earnings for date: {}", date);

        try {
            EarningsDetailedResponseDto response = unusualWhalesClient.getAfterhoursEarnings(date, null, null);

            if (response != null && response.data() != null) {
                log.info("Retrieved {} afterhours earnings for {}", response.data().size(), date);
                // Convert detailed items to simple items for backward compatibility
                return response.data().stream()
                    .map(item -> new EarningsItemDto(item.symbol(), item.fullName(), item.marketcap(), item.reportTime()))
                    .toList();
            } else {
                log.warn("No afterhours earnings returned from API for {}", date);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching afterhours earnings for date: {}", date, e);
            throw new MarketDataProviderException("Failed to fetch afterhours earnings: " + e.getMessage(), e);
        }
    }
    
    /**
     * Fetch detailed premarket earnings with all fields
     *
     * @param date Date in YYYY-MM-DD format (optional)
     * @param limit Number of results (default: 50, max: 100, min: 1)
     * @param page Page number for pagination (optional)
     * @return List of detailed earnings items for premarket
     */
    @Cacheable(value = "earningsCache", 
               key = "'premarket-detailed-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null')", 
               cacheManager = "earningsCacheManager")
    public List<EarningsDetailedItemDto> getPremarketEarningsDetailed(String date, Integer limit, Integer page) {
        log.debug("Fetching detailed premarket earnings for date: {}, limit: {}, page: {}", date, limit, page);

        try {
            EarningsDetailedResponseDto response = unusualWhalesClient.getPremarketEarnings(date, limit, page);

            if (response != null && response.data() != null) {
                log.info("Retrieved {} detailed premarket earnings for {}", response.data().size(), date != null ? date : "today");
                return response.data();
            } else {
                log.warn("No detailed premarket earnings returned from API for {}", date);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching detailed premarket earnings for date: {}, limit: {}, page: {}", date, limit, page, e);
            throw new MarketDataProviderException("Failed to fetch detailed premarket earnings: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch detailed afterhours earnings with all fields
     *
     * @param date Date in YYYY-MM-DD format (optional)
     * @param limit Number of results (default: 50, max: 100, min: 1)
     * @param page Page number for pagination (optional)
     * @return List of detailed earnings items for afterhours
     */
    @Cacheable(value = "earningsCache", 
               key = "'afterhours-detailed-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null')", 
               cacheManager = "earningsCacheManager")
    public List<EarningsDetailedItemDto> getAfterhoursEarningsDetailed(String date, Integer limit, Integer page) {
        log.debug("Fetching detailed afterhours earnings for date: {}, limit: {}, page: {}", date, limit, page);

        try {
            EarningsDetailedResponseDto response = unusualWhalesClient.getAfterhoursEarnings(date, limit, page);

            if (response != null && response.data() != null) {
                log.info("Retrieved {} detailed afterhours earnings for {}", response.data().size(), date != null ? date : "today");
                return response.data();
            } else {
                log.warn("No detailed afterhours earnings returned from API for {}", date);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching detailed afterhours earnings for date: {}, limit: {}, page: {}", date, limit, page, e);
            throw new MarketDataProviderException("Failed to fetch detailed afterhours earnings: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch earnings data for a specific ticker
     *
     * @param ticker Stock ticker symbol
     * @return Ticker earnings response containing earnings data
     */
    @Cacheable(value = "earningsCache", key = "'ticker-' + #ticker", cacheManager = "earningsCacheManager")
    public TickerEarningsResponseDto getTickerEarnings(String ticker) {
        log.debug("Fetching ticker earnings for {}", ticker);

        try {
            TickerEarningsResponseDto response = unusualWhalesClient.getTickerEarnings(ticker);

            if (response != null && response.data() != null) {
                log.info("Retrieved {} earnings data points for {}", response.data().size(), ticker);
            } else {
                log.warn("No earnings data returned from API for {}", ticker);
            }

            return response;
        } catch (Exception e) {
            log.error("Error fetching ticker earnings for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch ticker earnings: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch earnings by date (combines premarket and afterhours)
     *
     * @param date Date in YYYY-MM-DD format
     * @return Earnings by date response containing both premarket and afterhours
     */
    @Cacheable(value = "earningsCache", key = "'by-date-' + #date", cacheManager = "earningsCacheManager")
    public EarningsByDateResponseDto getEarningsByDate(String date) {
        log.debug("Fetching earnings by date for {}", date);

        try {
            List<EarningsItemDto> premarket = proxy().getPremarketEarnings(date);
            List<EarningsItemDto> afterhours = proxy().getAfterhoursEarnings(date);

            EarningsByDateResponseDto response = new EarningsByDateResponseDto(
                date,
                premarket != null ? premarket : List.of(),
                afterhours != null ? afterhours : List.of(),
                true
            );

            log.info("Retrieved {} premarket and {} afterhours earnings for {}",
                response.premarket().size(), response.afterhours().size(), date);

            return response;
        } catch (Exception e) {
            log.error("Error fetching earnings by date for {}", date, e);
            throw new MarketDataProviderException("Failed to fetch earnings by date: " + e.getMessage(), e);
        }
    }
}
