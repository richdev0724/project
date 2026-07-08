package com.marketmono.dto;

import java.util.List;

/**
 * Response DTO for all option contracts for a ticker from upstream market data API
 * Endpoint: GET /api/stock/{ticker}/option-contracts
 * 
 * Contains all option contracts for the given ticker with various filter options
 */
public record OptionContractsAllResponseDto(
    List<OptionContractItemDto> data
) {
}



