package com.marketmono.dto;

import java.util.List;

/**
 * Response DTO for historic option contract data from upstream market data API
 * Endpoint: GET /api/option-contract/{id}/historic
 * 
 * Contains historic data for each trading day of an option contract
 */
public record OptionContractHistoricResponseDto(
    List<OptionContractHistoricChainDto> chains
) {
}



