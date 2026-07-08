package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.InstitutionalOwnershipResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for fetching institutional ownership data from the upstream market data API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionalOwnershipService {

    private final UnusualWhalesClient unusualWhalesClient;

    /**
     * Fetch institutional ownership for a ticker with optional filters
     */
    public InstitutionalOwnershipResponseDto getInstitutionalOwnership(
            String ticker,
            String date,
            String startDate,
            String endDate,
            Integer limit,
            Integer page,
            String order,
            String orderDirection,
            List<String> tags) {
        log.debug(
            "Fetching institutional ownership from upstream market data API - ticker: {}, limit: {}",
            ticker,
            limit
        );

        try {
            InstitutionalOwnershipResponseDto response = unusualWhalesClient.getInstitutionalOwnership(
                    ticker, date, startDate, endDate, limit, page, order, orderDirection, tags
            );

            if (response != null && response.data() != null) {
                log.info("Retrieved {} institutional ownership records for {}", response.data().size(), ticker);
            } else {
                log.warn("No institutional ownership data returned for {}", ticker);
            }

            return response;
        } catch (Exception e) {
            log.error("Error fetching institutional ownership for {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch institutional ownership: " + e.getMessage(), e);
        }
    }
}
