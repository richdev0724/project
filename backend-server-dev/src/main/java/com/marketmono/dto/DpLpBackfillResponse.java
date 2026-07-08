package com.marketmono.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Response DTO for manual DP/LP backfill operations.
 */
public record DpLpBackfillResponse(
    String status,
    Integer tradesInserted,
    Integer aggregatesCreated,
    Map<String, Integer> tickerBreakdown,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    Long durationMs,
    String message
) {}
