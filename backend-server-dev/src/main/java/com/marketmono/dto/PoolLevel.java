package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Unified pool price level data for frontend consumption
 * Represents aggregated pool volume at a specific price level for both dark and lit pools
 */
public record PoolLevel(
    String ticker,
    double price,
    long size,

    @JsonProperty("total_premium")
    double totalPremium,

    int trades,

    @JsonProperty("market_session")
    String marketSession,

    @JsonProperty("trade_date")
    LocalDate tradeDate,

    @JsonProperty("pool_type")
    PoolType poolType
) {
    public enum PoolType {
        DARK_POOL,
        LIT_POOL
    }

    /**
     * Create a dark pool level
     */
    public static PoolLevel darkPool(String ticker, double price, long size, double totalPremium,
                                    int trades, String marketSession, LocalDate tradeDate) {
        return new PoolLevel(ticker, price, size, totalPremium, trades, marketSession, tradeDate, PoolType.DARK_POOL);
    }

    /**
     * Create a lit pool level
     */
    public static PoolLevel litPool(String ticker, double price, long size, double totalPremium,
                                   int trades, String marketSession, LocalDate tradeDate) {
        return new PoolLevel(ticker, price, size, totalPremium, trades, marketSession, tradeDate, PoolType.LIT_POOL);
    }
}
