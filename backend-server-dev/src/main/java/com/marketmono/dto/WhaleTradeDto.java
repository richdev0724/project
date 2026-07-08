package com.marketmono.dto;

/**
 * Whale Trade DTO for frontend consumption
 * Represents large options trades (whale threshold >= $10k premium)
 */
public record WhaleTradeDto(
    String id,
    String symbol,
    String price,
    Integer size,
    String total_premium,
    String market_session,
    String trade_date,
    String timestamp,
    String exchange_id
) {}
