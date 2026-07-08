package com.marketmono.controller;

import com.marketmono.dto.*;
import com.marketmono.entity.BucketSize;
import com.marketmono.entity.VenueType;
import com.marketmono.service.DpLpBackfillService;
import com.marketmono.service.DpLpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dp-lp")
@RequiredArgsConstructor
@Slf4j
public class DpLpController {

    private final DpLpService dpLpService;
    private final DpLpBackfillService backfillService;

    /**
     * Get summary statistics for dark pool and lit pool data.
     * 
     * @param ticker Stock ticker symbol
     * @param window Time window (1d, 5d, 1w, 1m, 3m)
     * @return Summary with KPIs for both venues
     */
    @GetMapping("/summary/{ticker}")
    public ResponseEntity<DpLpSummaryDto> getSummary(
        @PathVariable String ticker,
        @RequestParam(defaultValue = "1d") String window
    ) {
        log.info("GET /api/v1/dp-lp/summary/{} with window: {}", ticker, window);
        
        DpLpSummaryDto summary = dpLpService.getSummary(ticker.toUpperCase(), window);
        return ResponseEntity.ok(summary);
    }

    /**
     * Get top dark-pool price levels by total premium from stored trades.
     * DB-only; never calls the upstream market data API.
     *
     * @param ticker Stock ticker symbol
     * @param days Number of days to look back (default: 15)
     * @param limit Maximum number of levels (default: 5)
     */
    @GetMapping("/levels/{ticker}")
    public ResponseEntity<List<DpLpTopLevelDto>> getTopDarkPoolLevels(
        @PathVariable String ticker,
        @RequestParam(defaultValue = "15") int days,
        @RequestParam(defaultValue = "5") int limit
    ) {
        log.info("GET /api/v1/dp-lp/levels/{} - days={}, limit={}", ticker, days, limit);

        int cappedDays = Math.min(Math.max(days, 1), 30);
        int cappedLimit = Math.min(Math.max(limit, 1), 200);

        List<DpLpTopLevelDto> levels = dpLpService.getTopDarkPoolLevels(
            ticker.toUpperCase(), cappedDays, cappedLimit
        );
        return ResponseEntity.ok(levels);
    }

    /**
     * Get time series data for a specific venue type.
     * 
     * @param ticker Stock ticker symbol
     * @param venue Venue type (dark or lit)
     * @param bucket Bucket size (1m, 5m, 1h, 1d)
     * @param window Time window (1d, 5d, 1w, 1m, 3m)
     * @return Time series data for charts
     */
    @GetMapping("/series/{ticker}")
    public ResponseEntity<DpLpSeriesDto> getSeries(
        @PathVariable String ticker,
        @RequestParam(defaultValue = "dark") String venue,
        @RequestParam(defaultValue = "5m") String bucket,
        @RequestParam(defaultValue = "1d") String window
    ) {
        log.info("GET /api/v1/dp-lp/series/{} with venue: {}, bucket: {}, window: {}", 
            ticker, venue, bucket, window);
        
        VenueType venueType = VenueType.fromString(venue);
        BucketSize bucketSize = BucketSize.fromString(bucket);
        
        DpLpSeriesDto series = dpLpService.getSeries(
            ticker.toUpperCase(), venueType, bucketSize, window
        );
        
        return ResponseEntity.ok(series);
    }

    /**
     * Manual backfill endpoint for DP/LP data.
     * 
     * @param request Backfill parameters (tickers, date range or daysBack)
     * @return Backfill operation results
     */
    @PostMapping("/backfill")
    public ResponseEntity<DpLpBackfillResponse> backfill(
        @RequestBody DpLpBackfillRequest request
    ) {
        log.info("POST /api/v1/dp-lp/backfill with tickers: {}", request.tickers());
        
        DpLpBackfillResponse response = backfillService.backfillData(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Quick backfill endpoint with query parameters (convenience method).
     * 
     * @param ticker Single ticker to backfill
     * @param days Number of days to backfill (default: 7)
     * @return Backfill operation results
     */
    @PostMapping("/backfill/{ticker}")
    public ResponseEntity<DpLpBackfillResponse> quickBackfill(
        @PathVariable String ticker,
        @RequestParam(defaultValue = "7") Integer days
    ) {
        log.info("POST /api/v1/dp-lp/backfill/{} with days: {}", ticker, days);
        
        DpLpBackfillRequest request = new DpLpBackfillRequest(
            List.of(ticker.toUpperCase()),
            null,
            null,
            days
        );
        
        DpLpBackfillResponse response = backfillService.backfillData(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for DP/LP service.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("DP/LP service is healthy");
    }
}
