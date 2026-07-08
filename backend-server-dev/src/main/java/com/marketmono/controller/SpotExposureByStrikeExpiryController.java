package com.marketmono.controller;

import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.service.SpotExposureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for spot exposure by expiry-strike data from upstream market data provider
 * Endpoint: GET /api/v1/spot-exposure-by-strike-expiry/{ticker}
 */
@RestController
@RequestMapping("/api/v1/spot-exposure-by-strike-expiry")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SpotExposureByStrikeExpiryController {
    
    private final SpotExposureService spotExposureService;
    
    /**
     * Get spot exposure data aggregated by expiry and strike for a ticker
     * 
     * @param ticker Stock ticker symbol
     * @param expirations Array of expiration dates (required)
     * @param date Market date (optional, defaults to most recent trading day)
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @param page Page number for pagination
     * @param minStrike Minimum strike price filter
     * @param maxStrike Maximum strike price filter
     * @param minDte Minimum days to expiry filter
     * @param maxDte Maximum days to expiry filter
     * @return Spot exposure data grouped by expiry and strike
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getSpotExposureByExpiryStrike(
            @PathVariable String ticker,
            @RequestParam(required = true) List<String> expirations,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "min_strike", required = false) Integer minStrike,
            @RequestParam(name = "max_strike", required = false) Integer maxStrike,
            @RequestParam(name = "min_dte", required = false) Integer minDte,
            @RequestParam(name = "max_dte", required = false) Integer maxDte
    ) {
        log.info("GET /api/v1/spot-exposure-by-strike-expiry/{} - expirations: {} (total: {}), date: {}, limit: {}", 
                 ticker, expirations.size() > 5 ? expirations.subList(0, 5) + "..." : expirations, 
                 expirations.size(), date, limit);
        
        try {
            List<SpotExposureByStrikeDto> data = 
                spotExposureService.getSpotExposureByExpiryStrike(
                    ticker, expirations, date, limit, page, 
                    minStrike, maxStrike, minDte, maxDte
                );
            
            log.info("Returning {} expiry-strike spot exposure data points for {}", 
                data.size(), ticker);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            log.error("Error fetching spot exposure by expiry-strike for {}", ticker, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}


