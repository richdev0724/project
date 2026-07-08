package com.marketmono.dto;

import java.util.List;

/**
 * Response DTO for intraday option contract data from upstream market data API
 * Endpoint: GET /api/option-contract/{id}/intraday
 * 
 * Contains 1-minute interval intraday data for an option contract
 */
public record OptionContractIntradayResponseDto(
    List<OptionContractIntradayDataDto> data
) {
}



