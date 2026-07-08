package com.marketmono.dto;

/**
 * One-shot reply sent when a client subscribes to
 * {@code /app/strata-prices/{ticker}}. When {@code tracked} is false the
 * ticker is not saved in any CRM filter (or streaming is disabled) and the
 * frontend should fall back to the existing REST pipeline. {@code tick} is
 * the last known price and may be null when no tick has been seen yet.
 */
public record CrmPriceStreamSnapshotDto(
    String ticker,
    boolean tracked,
    CrmPriceStreamTickDto tick
) {}
