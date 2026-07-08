package com.marketmono.dto;

/**
 * Request DTO for market data dark pool endpoint
 * Matches TypeScript interface from frontend: MarketDataQueryParams
 *
 * @param showLevels Whether to include price levels in response
 * @param showBlockLevels Whether to include block-level data
 * @param ticker Stock ticker symbol to query
 * @param startDate Start date for data range (YYYY-MM-DD)
 * @param endDate End date for data range (YYYY-MM-DD)
 * @param levelsAmount Number of price levels to return
 * @param blockLevelsAmount Number of block levels to return
 */
public record MarketDataQueryRequest(
    boolean showLevels,
    boolean showBlockLevels,
    String ticker,
    String startDate,
    String endDate,
    int levelsAmount,
    int blockLevelsAmount
) {
}
