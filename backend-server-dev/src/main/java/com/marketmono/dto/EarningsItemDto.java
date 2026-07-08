package com.marketmono.dto;

/**
 * DTO for earnings item data from upstream market data API
 */
public record EarningsItemDto(
    String symbol,
    String full_name,
    String marketcap,
    String time
) {}