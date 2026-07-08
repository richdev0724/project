package com.marketmono.dto;

/**
 * Result of a sync operation
 */
public record SyncResult(
    String status,
    String message,
    int fetchedCount,
    int savedCount,
    int skippedCount
) {
    public static SyncResult success(int fetchedCount, int savedCount, int skippedCount) {
        return new SyncResult(
            "SUCCESS",
            String.format("Fetched %d, saved %d, skipped %d", fetchedCount, savedCount, skippedCount),
            fetchedCount,
            savedCount,
            skippedCount
        );
    }
    
    public static SyncResult skipped(String reason) {
        return new SyncResult("SKIPPED", reason, 0, 0, 0);
    }
    
    public static SyncResult error(String message) {
        return new SyncResult("ERROR", message, 0, 0, 0);
    }
}
