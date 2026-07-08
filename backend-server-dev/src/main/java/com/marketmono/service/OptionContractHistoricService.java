package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionContractHistoricChainDto;
import com.marketmono.dto.OptionContractHistoricResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Service for fetching historic option contract data from upstream market data API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptionContractHistoricService {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get historic data for an option contract
     * 
     * @param contractId Option contract in ISO format (e.g., TSLA230526P00167500)
     * @param limit Optional limit for number of results
     * @return List of historic data points for the contract
     */
    public List<OptionContractHistoricChainDto> getOptionContractHistoric(String contractId, Integer limit) {
        log.info("Fetching historic data for option contract: {}, limit: {}", contractId, limit);
        
        OptionContractHistoricResponseDto response = unusualWhalesClient.getOptionContractHistoric(contractId, limit);
        
        if (response == null || response.chains() == null) {
            log.warn("No historic data found for option contract: {}", contractId);
            return Collections.emptyList();
        }
        
        log.info("Retrieved {} historic data points for option contract: {}", response.chains().size(), contractId);
        return response.chains();
    }
}



