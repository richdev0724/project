package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.EtfHoldingsResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for fetching ETF holdings data from the upstream market data API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EtfHoldingsService {

    private final UnusualWhalesClient unusualWhalesClient;

    public EtfHoldingsResponseDto getEtfHoldings(String ticker, String date, Integer limit, Integer page) {
        log.debug("Fetching ETF holdings from upstream market data API - ticker: {}, limit: {}", ticker, limit);

        try {
            EtfHoldingsResponseDto response = unusualWhalesClient.getEtfHoldings(ticker, date, limit, page);

            if (response != null && response.data() != null) {
                log.info("Retrieved {} ETF holdings for {}", response.data().size(), ticker);
            } else {
                log.warn("No ETF holdings data returned for {}", ticker);
            }

            return response;
        } catch (Exception e) {
            log.error("Error fetching ETF holdings for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch ETF holdings: " + e.getMessage(), e);
        }
    }
}
