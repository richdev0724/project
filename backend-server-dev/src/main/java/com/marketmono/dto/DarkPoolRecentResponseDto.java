package com.marketmono.dto;

import java.util.List;

/**
 * Response DTO for recent dark pool trades from upstream market data API
 * Endpoint: GET /api/darkpool/recent
 */
public record DarkPoolRecentResponseDto(
    List<DarkPoolRecentTradeDto> data
) {
}



