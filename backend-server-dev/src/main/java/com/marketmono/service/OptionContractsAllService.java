package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionContractItemDto;
import com.marketmono.dto.OptionContractsAllResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for fetching all option contracts for a ticker from upstream market data API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptionContractsAllService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get all option contracts for a ticker with optional filters
     * 
     * @param ticker Stock ticker symbol
     * @param expiry Single expiry date in ISO date format (YYYY-MM-DD)
     * @param optionType Option type to filter by ("call", "Call", "put", "Put")
     * @param volGreaterOi Only return chains where volume > open interest
     * @param excludeZeroVolChains Only return chains where volume > 0
     * @param excludeZeroDte Only return chains which do not expire on the same day
     * @param excludeZeroOiChains Only return chains where open interest > 0
     * @param maybeOtmOnly Only return chains which are out of the money
     * @param optionSymbols Options symbols to filter by
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @param page Page number for pagination (starts on page 0)
     * @return List of option contracts matching the filters
     */
    public List<OptionContractItemDto> getOptionContractsAll(
            String ticker,
            String expiry,
            String optionType,
            Boolean volGreaterOi,
            Boolean excludeZeroVolChains,
            Boolean excludeZeroDte,
            Boolean excludeZeroOiChains,
            Boolean maybeOtmOnly,
            List<String> optionSymbols,
            Integer limit,
            Integer page) {
        
        log.info("Fetching all option contracts for ticker: {}, expiry: {}, optionType: {}, limit: {}", 
                 ticker, expiry, optionType, limit);
        
        OptionContractsAllResponseDto response = unusualWhalesClient.getOptionContractsAll(
            ticker, expiry, optionType, volGreaterOi, excludeZeroVolChains, 
            excludeZeroDte, excludeZeroOiChains, maybeOtmOnly, optionSymbols, limit, page
        );
        
        if (response == null || response.data() == null) {
            log.warn("No option contracts found for ticker: {}", ticker);
            return Collections.emptyList();
        }
        
        log.info("Retrieved {} option contracts for ticker: {}", response.data().size(), ticker);
        return response.data();
    }
}



