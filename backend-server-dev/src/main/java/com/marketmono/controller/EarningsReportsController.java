package com.marketmono.controller;

import com.marketmono.dto.EarningsDetailedItemDto;
import com.marketmono.service.EarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller for combined earnings reports (premarket + afterhours)
 * Endpoint: GET /api/v1/earnings-reports
 */
@RestController
@RequestMapping("/api/v1/earnings-reports")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EarningsReportsController {
    
    private final EarningsService earningsService;
    
    /**
     * Get combined earnings reports (premarket and afterhours) for a specific date
     * Returns all earnings with detailed information in a single array
     * 
     * @param date Date in YYYY-MM-DD format (optional, defaults to today)
     * @return Combined premarket and afterhours earnings data
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getEarningsReports(
            @RequestParam(required = false) String date) {
        log.info("GET /api/v1/earnings-reports - date: {}", date);

        try {
            // Fetch both premarket and afterhours earnings
            List<EarningsDetailedItemDto> premarketData = earningsService.getPremarketEarningsDetailed(date, null, null);
            List<EarningsDetailedItemDto> afterhoursData = earningsService.getAfterhoursEarningsDetailed(date, null, null);
            
            // Combine both lists into a single data array
            List<EarningsDetailedItemDto> combinedData = new ArrayList<>();
            if (premarketData != null) {
                combinedData.addAll(premarketData);
            }
            if (afterhoursData != null) {
                combinedData.addAll(afterhoursData);
            }
            
            log.info("Returning {} total earnings reports ({} premarket, {} afterhours) for {}", 
                     combinedData.size(), 
                     premarketData != null ? premarketData.size() : 0,
                     afterhoursData != null ? afterhoursData.size() : 0,
                     date != null ? date : "today");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", combinedData
            ));
        } catch (Exception e) {
            log.error("Error fetching earnings reports - date: {}", date, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}



