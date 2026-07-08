package com.marketmono.dto;

import java.time.Instant;

/**
 * Runtime + persisted collection or stream status for a configured spot GEX ticker.
 */
public record SpotGexCollectionStatusDto(
    String ticker,
    Instant lastCollectedAt,
    long rowsToday,
    long collectionCycles,
    Instant lastAttemptAt,
    Instant lastSuccessfulCollectionAt,
    String lastError,
    int errorCount,
    boolean lastCycleSuccessful,
    boolean healthy,
    String sourceMode,
    boolean streamSubscribed,
    Instant latestStreamBucketStart,
    Long latestStreamBucketLagMillis,
    boolean streamStale,
    long streamRowsToday,
    long streamRowsLatestSession,
    long streamCurrentRows
) {
    public SpotGexCollectionStatusDto(
        String ticker,
        Instant lastCollectedAt,
        long rowsToday,
        long collectionCycles,
        Instant lastAttemptAt,
        Instant lastSuccessfulCollectionAt,
        String lastError,
        int errorCount,
        boolean lastCycleSuccessful,
        boolean healthy
    ) {
        this(
            ticker,
            lastCollectedAt,
            rowsToday,
            collectionCycles,
            lastAttemptAt,
            lastSuccessfulCollectionAt,
            lastError,
            errorCount,
            lastCycleSuccessful,
            healthy,
            "legacy",
            false,
            null,
            null,
            false,
            0L,
            0L,
            0L
        );
    }
}
