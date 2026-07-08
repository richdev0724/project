package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionContractIntradayDataDto;
import com.marketmono.dto.OptionContractIntradayResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for fetching intraday option contract data from upstream market data API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptionContractIntradayService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get intraday data for an option contract
     * 
     * @param contractId Option contract in ISO format (e.g., GOOGL260220P00310000)
     * @param date Optional date in YYYY-MM-DD format (defaults to current/last market day)
     * @return List of 1-minute interval data points for the contract
     */
    public List<OptionContractIntradayDataDto> getOptionContractIntraday(String contractId, String date) {
        log.info("Fetching intraday data for option contract: {}, date: {}", contractId, date);
        
        OptionContractIntradayResponseDto response = unusualWhalesClient.getOptionContractIntraday(contractId, date);
        
        if (response == null || response.data() == null) {
            log.warn("No intraday data found for option contract: {}", contractId);
            return Collections.emptyList();
        }
        
        log.info("Retrieved {} intraday data points for option contract: {}", response.data().size(), contractId);
        return response.data();
    }
}



