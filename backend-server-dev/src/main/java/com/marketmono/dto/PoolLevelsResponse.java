package com.marketmono.dto;

import java.util.List;

/**
 * Unified response containing pool price levels for a ticker
 * Used for both dark pool and lit pool data
 */
public record PoolLevelsResponse(
    String ticker,
    List<PoolLevel> levels,
    int count,
    PoolLevel.PoolType poolType
) {
    /**
     * Create a dark pool response
     */
    public static PoolLevelsResponse darkPool(String ticker, List<PoolLevel> levels) {
        return new PoolLevelsResponse(ticker, levels, levels.size(), PoolLevel.PoolType.DARK_POOL);
    }

    /**
     * Create a lit pool response
     */
    public static PoolLevelsResponse litPool(String ticker, List<PoolLevel> levels) {
        return new PoolLevelsResponse(ticker, levels, levels.size(), PoolLevel.PoolType.LIT_POOL);
    }
}
