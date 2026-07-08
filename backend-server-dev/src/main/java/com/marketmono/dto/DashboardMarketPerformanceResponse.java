package com.marketmono.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Batched dashboard response for the market performance widget.
 */
public record DashboardMarketPerformanceResponse(
    String timeframe,
    String asOf,
    Map<String, TickerSeries> tickers
) {
    public record TickerSeries(
        BigDecimal previousSessionClose,
        List<SeriesPoint> series
    ) {}

    public record SeriesPoint(
        long timestamp,
        String date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
    ) {}
}
