package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.PriceChangePerMonthItemDto;
import com.marketmono.dto.PriceChangePerMonthResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for fetching seasonality data (price change per month)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeasonalityService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get price change per month data for a ticker
     * Returns the relative price change for all past months over multiple years
     * 
     * @param ticker Stock ticker symbol
     * @return List of price change per month items
     */
    public List<PriceChangePerMonthItemDto> getPriceChangePerMonth(String ticker) {
        log.info("Fetching price change per month data for ticker: {}", ticker);
        
        try {
            PriceChangePerMonthResponseDto response = unusualWhalesClient.getPriceChangePerMonth(ticker.toUpperCase());
            
            if (response == null || response.data() == null) {
                log.warn("No price change per month data available for ticker: {}", ticker);
                return Collections.emptyList();
            }
            
            log.info("Successfully fetched {} price change per month data points for {}", 
                     response.data().size(), ticker);
            return response.data();
            
        } catch (Exception e) {
            log.error("Error fetching price change per month data for ticker: {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch price change per month data: " + e.getMessage(), e);
        }
    }
}


