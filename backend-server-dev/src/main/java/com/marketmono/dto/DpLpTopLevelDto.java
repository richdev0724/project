package com.marketmono.dto;

import java.math.BigDecimal;

/**
 * A single top dark-pool price level aggregated from stored dp_lp_trades.
 *
 * @param price Display price (2 decimals)
 * @param totalSize Total shares traded at this level
 * @param totalPremium Total premium (sum of price * size)
 * @param trades Number of trades at this level
 */
public record DpLpTopLevelDto(
    BigDecimal price,
    long totalSize,
    BigDecimal totalPremium,
    long trades
) {
}
