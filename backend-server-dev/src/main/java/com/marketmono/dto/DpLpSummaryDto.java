package com.marketmono.dto;

import com.marketmono.entity.VenueType;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for DP/LP summary response (dashboard KPIs).
 */
public record DpLpSummaryDto(
    String ticker,
    String window,
    VenueSummary darkPool,
    VenueSummary litPool,
    BigDecimal darkToLitRatio,
    List<PriceLevel> topLevels
) {
    public record VenueSummary(
        VenueType venueType,
        Integer totalTrades,
        Long totalVolume,
        BigDecimal vwap,
        Long netImbalance
    ) {}

    public record PriceLevel(
        BigDecimal price,
        Long darkVolume,
        Long litVolume,
        Long netVolume
    ) {}
}
