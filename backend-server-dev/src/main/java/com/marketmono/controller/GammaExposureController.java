package com.marketmono.controller;

import com.marketmono.dto.GreekExposureByExpiryDto;
import com.marketmono.dto.GreekExposureByStrikeDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.GammaExposureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for gamma exposure and greek data from upstream market data provider
 * Endpoints:
 *   GET /api/v1/gamma-exposure/{ticker}/expirations
 *   GET /api/v1/gamma-exposure/{ticker}
 */
@RestController
@RequestMapping("/api/v1/gamma-exposure")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class GammaExposureController {
    
    private final GammaExposureService gammaExposureService;
    
    /**
     * Get available expiration dates for a ticker
     * Used by frontend to populate expiration date picker
     * 
     * @param ticker Stock ticker symbol
     * @return List of expiration dates
     */
    @GetMapping("/{ticker}/expirations")
    public ResponseEntity<Map<String, Object>> getExpirationDates(@PathVariable String ticker) {
        log.info("GET /api/v1/gamma-exposure/{}/expirations", ticker);
        
        try {
            List<String> expirations = gammaExposureService.getExpirationDates(ticker);
            
            log.info("Returning {} expiration dates for {}", expirations.size(), ticker);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", expirations
            ));
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching expiration dates for {}", ticker, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get gamma exposure data for a ticker
     * Query param determines aggregation: expiration (date) or strike
     * 
     * @param ticker Stock ticker symbol
     * @param expiration Expiration date filter (if null, returns by strike for latest date)
     * @param date Market date (optional)
     * @return Gamma exposure data grouped by strike or expiry
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getGammaExposure(
            @PathVariable String ticker,
            @RequestParam(required = false) String expiration,
            @RequestParam(required = false) String date
    ) {
        log.info("GET /api/v1/gamma-exposure/{} - expiration: {}, date: {}", ticker, expiration, date);
        
        try {
            // If expiration is specified, return by strike for that expiration
            // Otherwise return by expiry to show all expirations
            if (expiration != null) {
                List<GreekExposureByStrikeDto> strikeData = 
                    gammaExposureService.getGammaExposureByStrike(ticker, date);
                
                log.info("Returning {} strike-level gamma exposure data points for {}", 
                    strikeData.size(), ticker);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", strikeData,
                    "lastPrice", 0 // TODO: Add current price from market data service
                ));
            } else {
                List<GreekExposureByExpiryDto> expiryData = 
                    gammaExposureService.getGammaExposureByExpiry(ticker, date);
                
                log.info("Returning {} expiry-level gamma exposure data points for {}", 
                    expiryData.size(), ticker);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", expiryData
                ));
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching gamma exposure for {}", ticker, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
