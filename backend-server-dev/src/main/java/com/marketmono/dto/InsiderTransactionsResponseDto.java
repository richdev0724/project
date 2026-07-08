package com.marketmono.dto;

import java.util.List;

/**
 * Response DTO for insider transactions from upstream market data API
 * Endpoint: GET /api/insider/transactions
 */
public record InsiderTransactionsResponseDto(
    List<InsiderTransactionDto> data
) {
}





