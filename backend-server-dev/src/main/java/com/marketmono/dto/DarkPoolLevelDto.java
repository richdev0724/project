package com.marketmono.dto;

import java.math.BigDecimal;

/**
 * DTO representing a single dark pool price level
 * Matches TypeScript interface from frontend: DarkPoolLevel
 *
 * @param ticker Stock ticker symbol
 * @param price Price level
 * @param size Volume/size at this price level
 * @param totalPremium Total premium (size * price)
 * @param trades Number of trades at this level
 * @param marketSession Market session (Pre Market, During Market, After Market)
 * @param tradeDate Trade date in YYYY-MM-DD format
 */
public record DarkPoolLevelDto(
    String ticker,
    BigDecimal price,
    long size,
    BigDecimal totalPremium,
    int trades,
    String marketSession,
    String tradeDate
) {
}
