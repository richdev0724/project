package com.marketmono.controller;

import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.service.SpotExposureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for spot exposure data from upstream market data provider
 * Endpoints:
 *   GET /api/v1/spot-exposure/{ticker}
 *   GET /api/v1/spot-exposure/{ticker}/expiry-strike
 */
@RestController
@RequestMapping("/api/v1/spot-exposure")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SpotExposureController {
    
    private final SpotExposureService spotExposureService;
    
    /**
     * Get spot exposure data aggregated by strike for a ticker
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date (optional, defaults to most recent trading day)
     * @param limit Number of results (default 50)
     * @param maxStrike Maximum strike price filter (optional)
     * @return Spot exposure data grouped by strike price
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getSpotExposure(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "max_strike", required = false) BigDecimal maxStrike
    ) {
        log.info("GET /api/v1/spot-exposure/{} - date: {}, limit: {}, maxStrike: {}", 
                 ticker, date, limit, maxStrike);
        
        try {
            List<SpotExposureByStrikeDto> strikeData = 
                spotExposureService.getSpotExposureByStrike(ticker, date, limit, maxStrike);
            
            log.info("Returning {} strike-level spot exposure data points for {}", 
                strikeData.size(), ticker);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", strikeData
            ));
        } catch (Exception e) {
            log.error("Error fetching spot exposure for {}", ticker, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
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
    @GetMapping("/{ticker}/expiry-strike")
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
        log.info("GET /api/v1/spot-exposure/{}/expiry-strike - expirations: {}, date: {}, limit: {}", 
                 ticker, expirations, date, limit);
        
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


