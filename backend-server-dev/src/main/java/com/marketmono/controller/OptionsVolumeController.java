package com.marketmono.controller;

import com.marketmono.dto.OptionsVolumeDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.TickerOptionsVolumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for stock options volume endpoints.
 * Serves dashboard tickers from our database (populated every minute by the
 * sync scheduler); falls back to the upstream market data API for tickers or
 * history depths not stored locally.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class OptionsVolumeController {
    
    private final TickerOptionsVolumeService tickerOptionsVolumeService;
    
    /**
     * Get options volume for a specific ticker
     * Endpoint: GET /api/v1/ticker-options-volume
     * 
     * Returns the options volume & premium for all trade executions
     * that happened on a given trading date for the given ticker.
     * 
     * @param ticker Stock ticker symbol (query parameter)
     * @param limit Number of results (optional, default: 10, max: 500, min: 1)
     * @return Options volume response (database-backed, upstream fallback)
     */
    @GetMapping("/ticker-options-volume")
    public ResponseEntity<OptionsVolumeDto> getOptionsVolume(
            @RequestParam String ticker,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        log.info("GET /api/v1/ticker-options-volume - ticker: {}, limit: {}", ticker, limit);
        
        try {
            ticker = ticker.toUpperCase();
            
            // Validate limit range
            if (limit < 1 || limit > 500) {
                log.warn("Invalid limit: {}. Must be between 1 and 500", limit);
                return ResponseEntity.badRequest().build();
            }
            
            // Database-first read; proxies upstream only when the DB can't satisfy the request.
            OptionsVolumeDto response = tickerOptionsVolumeService.getOptionsVolume(ticker, limit);
            
            if (response != null && response.data() != null) {
                log.info("Returning {} options volume records for {}", response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No options volume data available for {}", ticker);
                return ResponseEntity.ok(new OptionsVolumeDto(java.util.List.of()));
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching options volume for {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
