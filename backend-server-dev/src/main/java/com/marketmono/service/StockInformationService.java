package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockInformationResponse;
import com.marketmono.exception.MarketDataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for fetching stock information from upstream market data API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockInformationService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get detailed stock information (company details, market cap, sector, etc.)
     * 
     * @param ticker Stock ticker symbol
     * @return Stock information response with company and stock details
     */
    public StockInformationResponse getStockInformation(String ticker) {
        log.info("Fetching stock information for ticker: {}", ticker);
        
        try {
            StockInformationResponse response = unusualWhalesClient.getStockInformation(ticker.toUpperCase());
            
            if (response == null || response.data() == null) {
                log.warn("No stock information available for ticker: {}", ticker);
                return null;
            }
            
            log.info("Successfully fetched stock information for {}: {} - Sector: {}, Market Cap: ${}", 
                     ticker, 
                     response.data().fullName(), 
                     response.data().sector(),
                     response.data().marketcap());
            return response;
            
        } catch (Exception e) {
            log.error("Error fetching stock information for ticker: {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch stock information: " + e.getMessage(), e);
        }
    }
}


