package com.marketmono.dto;

import java.math.BigDecimal;

/**
 * Filters applied to whale trades query
 */
public record WhaleTradeFilters(
    BigDecimal minPremium,
    BigDecimal maxPremium,
    String session,
    String ticker
) {}
