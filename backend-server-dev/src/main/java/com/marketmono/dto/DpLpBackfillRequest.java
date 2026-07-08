package com.marketmono.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for manual DP/LP data backfill.
 */
public record DpLpBackfillRequest(
    List<String> tickers,
    LocalDate startDate,
    LocalDate endDate,
    Integer daysBack  // Alternative to date range: "last N days"
) {
    public DpLpBackfillRequest {
        if (tickers == null || tickers.isEmpty()) {
            throw new IllegalArgumentException("At least one ticker is required");
        }
        if (startDate == null && endDate == null && daysBack == null) {
            throw new IllegalArgumentException("Either date range or daysBack must be specified");
        }
    }
}
