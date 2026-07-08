package com.marketmono.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * WebSocket summary payload for latest collected spot GEX snapshot data.
 */
public record SpotGexTickerUpdateDto(
    String ticker,
    Instant collectedAt,
    int rowCount,
    List<StrikeSummary> topGammaStrikes
) {
    public record StrikeSummary(
        LocalDate expiry,
        BigDecimal strike,
        BigDecimal price,
        BigDecimal callGammaOi,
        BigDecimal putGammaOi,
        BigDecimal netGamma
    ) {}
}