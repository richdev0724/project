package com.marketmono.dto;

import java.time.Instant;
import java.util.List;

/**
 * Stream ingestion and bucket freshness status for Spot GEX rollout checks.
 */
public record SpotGexStreamStatusDto(
    String sourceMode,
    String nodeId,
    String ownerNodeId,
    boolean owner,
    String transport,
    boolean active,
    boolean connected,
    int activeTickerCount,
    int pendingEventCount,
    List<String> subscribedTickers,
    Instant lastEventAt,
    String lastEventTicker,
    Instant lastFlushAt,
    Instant lastPersistedBucketStart,
    Long lastPersistedBucketLagMillis,
    boolean stale,
    long staleThresholdMillis,
    long staleTickerCount,
    List<TickerStatus> tickers
) {
    public record TickerStatus(
        String ticker,
        boolean subscribed,
        Instant latestBucketStart,
        Long lagMillis,
        boolean stale
    ) {}
}
