package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.InsiderTransactionsResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.UnusualWhalesShedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for fetching insider transactions data from the upstream market data API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InsiderTransactionsService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Fetch insider transactions with optional ticker filter
     * 
     * @param tickerSymbol Optional ticker symbol to filter by
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @return Insider transactions response
     */
    public InsiderTransactionsResponseDto getInsiderTransactions(String tickerSymbol, Integer limit) {
        log.debug(
            "Fetching insider transactions from upstream market data API - ticker: {}, limit: {}",
            tickerSymbol,
            limit
        );
        
        try {
            InsiderTransactionsResponseDto response = unusualWhalesClient.getInsiderTransactions(tickerSymbol, limit);
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} insider transactions", response.data().size());
            } else {
                log.warn("No insider transactions returned from API");
            }
            
            return response;
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching insider transactions", e);
            throw new MarketDataProviderException("Failed to fetch insider transactions: " + e.getMessage(), e);
        }
    }
}


