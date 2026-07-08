package com.marketmono.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StrataStockPriceLatestDto(
    String displayTicker,
    String sourceTicker,
    BigDecimal closePrice,
    BigDecimal prevClosePrice,
    BigDecimal changePercent,
    BigDecimal openPrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    Long volume,
    Long totalVolume,
    String marketTime,
    Instant tapeTime,
    Instant fetchedAt,
    boolean stale,
    long ageSeconds
) {
}
