package com.marketmono.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for dark pool scanner response
 * Matches TypeScript interface from frontend: {status: string, data?: any[], lastScanTime?: string}
 *
 * NOTE: This endpoint returns stored dark pool data, not an active scanner algorithm.
 * The Node.js backend had scanner services (darkPoolScanner.js, litFlowScanner.js) that analyzed
 * patterns/anomalies. This Java implementation focuses on returning aggregated data.
 * Future enhancement: Implement scanner algorithm to identify volume spikes, price levels, etc.
 *
 * @param status Scanner status ("complete", "running", "error")
 * @param data List of dark pool price levels (can be any type, usually DarkPoolLevelDto)
 * @param lastScanTime Timestamp of last scan operation
 */
public record DarkPoolScannerResponseDto(
    String status,
    List<?> data,
    LocalDateTime lastScanTime
) {
}
