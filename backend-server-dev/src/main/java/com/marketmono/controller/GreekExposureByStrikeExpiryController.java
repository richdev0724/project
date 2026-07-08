package com.marketmono.controller;

import com.marketmono.dto.GreekExposureByStrikeExpiryDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.GreekExposureByStrikeExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for greek exposure by strike and expiry endpoint
 * Provides greek exposure data (delta, gamma, charm, vanna) for a specific expiration
 * Endpoint: GET /api/v1/greek-exposure-strike-expiry/{ticker}
 */
@RestController
@RequestMapping("/api/v1/greek-exposure-strike-expiry")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class GreekExposureByStrikeExpiryController {

    private final GreekExposureByStrikeExpiryService greekExposureService;

    /**
     * Get greek exposure data aggregated by strike for a specific expiry
     * 
     * @param ticker Stock ticker symbol
     * @param expiry Expiration date (required, format: YYYY-MM-DD)
     * @param date Market date (optional, defaults to most recent trading day)
     * @return Greek exposure data grouped by strike for the expiry
     * 
     * Example response:
     * {
     *   "data": [
     *     {
     *       "call_charm": "102382359.5786",
     *       "call_delta": "227549667.4651",
     *       "call_gamma": "9356683.4241",
     *       "call_vanna": "152099632406.9564",
     *       "expiry": "2022-05-25",
     *       "put_charm": "-943028472.4815",
     *       "put_delta": "-191893077.7193",
     *       "put_gamma": "-12337386.0524",
     *       "put_vanna": "488921784213.1121",
     *       "strike": "150"
     *     }
     *   ]
     * }
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getGreekExposureByStrikeExpiry(
            @PathVariable String ticker,
            @RequestParam(required = true) String expiry,
            @RequestParam(required = false) String date
    ) {
        log.info("GET /api/v1/greek-exposure-strike-expiry/{} - expiry: {}, date: {}", 
                 ticker, expiry, date);
        
        try {
            List<GreekExposureByStrikeExpiryDto> data = 
                greekExposureService.getGreekExposureByStrikeExpiry(ticker, expiry, date);
            
            log.info("Returning {} greek exposure by strike-expiry data points for {} (expiry: {})", 
                     data.size(), ticker, expiry);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching greek exposure by strike-expiry for {} (expiry: {})", ticker, expiry, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}

