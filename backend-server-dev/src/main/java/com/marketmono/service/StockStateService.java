package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.exception.MarketDataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for fetching stock state data from upstream market data API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockStateService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get current stock state (price, volume, prev close) for a ticker
     * 
     * @param ticker Stock ticker symbol
     * @return Stock state response with current price and volume data
     */
    public StockStateResponse getStockState(String ticker) {
        log.info("Fetching stock state for ticker: {}", ticker);
        
        try {
            StockStateResponse response = unusualWhalesClient.getStockState(ticker.toUpperCase());
            
            if (response == null || response.data() == null) {
                log.warn("No stock state data available for ticker: {}", ticker);
                return null;
            }
            
            log.info("Successfully fetched stock state for {}: close=${}, volume={}", 
                     ticker, response.data().close(), response.data().totalVolume());
            return response;
            
        } catch (Exception e) {
            log.error("Error fetching stock state for ticker: {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch stock state: " + e.getMessage(), e);
        }
    }
}


