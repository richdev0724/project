package com.marketmono.controller;

import com.marketmono.dto.SectorPerformanceLiveResponse;
import com.marketmono.dto.SectorPerformanceResponse;
import com.marketmono.service.SectorEtfCollectionService;
import com.marketmono.service.SectorPerformanceService;
import com.marketmono.service.SectorPerformanceSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sector-performance")
@RequiredArgsConstructor
@Slf4j
public class SectorPerformanceController {
    
    private final SectorPerformanceService sectorPerformanceService;
    private final SectorPerformanceSnapshotService sectorPerformanceSnapshotService;
    private final SectorEtfCollectionService sectorEtfCollectionService;
    
    /**
     * Get sector performance data for 11 sector ETFs
     * Cached for 5 minutes, queries pre-populated PostgreSQL database
     * 
     * @return List of sector performance metrics (1d, 30d, 6m)
     */
    @GetMapping
    public ResponseEntity<List<SectorPerformanceResponse>> getSectorPerformance() {
        log.info("GET /api/v1/sector-performance - Fetching sector ETF performance");
        
        List<SectorPerformanceResponse> performance = sectorPerformanceService.getSectorPerformance();
        
        log.info("Returning performance data for {} sectors", performance.size());
        return ResponseEntity.ok(performance);
    }
    
    /**
     * Get sector performance for the dashboard Sectors widget.
     * Served database-only from sector_performance_snapshots (refreshed every
     * minute during market hours by the scheduler); this endpoint never calls
     * the upstream market data API.
     * Returns performance metrics: lastClose, dailyPct, pct30d, pct6m, volume
     * 
     * @return List of sector performance metrics
     */
    @GetMapping("/live")
    public ResponseEntity<List<SectorPerformanceLiveResponse>> getLiveSectorPerformance() {
        log.info("GET /api/v1/sector-performance/live - Serving sector performance from database snapshots");
        
        List<SectorPerformanceLiveResponse> performance =
            sectorPerformanceSnapshotService.getSectorPerformanceFromDatabase();
        
        log.info("Returning performance data for {} sectors", performance.size());
        return ResponseEntity.ok(performance);
    }
    
    /**
     * Manual backfill endpoint for populating historical data
     * Example: POST /api/v1/sector-performance/backfill?date=2025-11-06
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfillDate(@RequestParam String date) {
        log.info("POST /api/v1/sector-performance/backfill - Manual backfill for date: {}", date);
        
        try {
            LocalDate targetDate = LocalDate.parse(date);
            sectorEtfCollectionService.backfillDate(targetDate);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Backfill completed for " + date,
                "date", date
            ));
        } catch (Exception e) {
            log.error("Backfill failed for date: {}", date, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Backfill failed: " + e.getMessage(),
                "date", date
            ));
        }
    }
}
