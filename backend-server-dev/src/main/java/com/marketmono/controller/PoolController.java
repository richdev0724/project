package com.marketmono.controller;

import com.marketmono.dto.PoolLevelsResponse;
import com.marketmono.service.PoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for pool trading data endpoints
 * Handles both dark pool (off-lit) and lit pool (exchange) data
 */
@RestController
@RequestMapping("/api/v1/pool")
@CrossOrigin(origins = "*")
public class PoolController {

    private static final Logger log = LoggerFactory.getLogger(PoolController.class);

    private final PoolService poolService;

    public PoolController(PoolService poolService) {
        this.poolService = poolService;
    }

    /**
     * Get dark pool price levels for a specific ticker
     *
     * @param ticker Stock ticker symbol
     * @param limit Maximum number of price levels to return (default: 10)
     * @param days Number of days to look back (default: 1)
     * @return Response containing dark pool levels sorted by total premium
     */
    @GetMapping("/dark-pool/levels/{ticker}")
    public ResponseEntity<PoolLevelsResponse> getDarkPoolLevels(
        @PathVariable String ticker,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(defaultValue = "7") int days
    ) {
        log.info("GET /api/pool/dark-pool/levels/{} - limit={}, days={}", ticker, limit, days);

        ticker = ticker.toUpperCase();

        // Validate parameters
        if (limit < 1 || limit > 500) {
            limit = 10;
        }
        if (days < 1 || days > 30) {
            days = 1;
        }

        PoolLevelsResponse response = poolService.getDarkPoolLevels(ticker, days, limit);

        log.debug("Returning {} dark pool levels for {}", response.count(), ticker);

        return ResponseEntity.ok(response);
    }

    /**
     * Get lit pool (exchange) price levels for a specific ticker
     *
     * @param ticker Stock ticker symbol
     * @param limit Maximum number of price levels to return (default: 10)
     * @param days Number of days to look back (default: 1)
     * @return Response containing lit pool levels sorted by total premium
     */
    @GetMapping("/lit-pool/levels/{ticker}")
    public ResponseEntity<PoolLevelsResponse> getLitPoolLevels(
        @PathVariable String ticker,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(defaultValue = "7") int days
    ) {
        log.info("GET /api/pool/lit-pool/levels/{} - limit={}, days={}", ticker, limit, days);

        ticker = ticker.toUpperCase();

        // Validate parameters
        if (limit < 1 || limit > 500) {
            limit = 10;
        }
        if (days < 1 || days > 30) {
            days = 1;
        }

        PoolLevelsResponse response = poolService.getLitPoolLevels(ticker, days, limit);

        log.debug("Returning {} lit pool levels for {}", response.count(), ticker);

        return ResponseEntity.ok(response);
    }
}
