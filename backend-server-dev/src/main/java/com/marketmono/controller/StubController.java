package com.marketmono.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stub controller for endpoints not available from the current market data provider.
 * Returns empty data instead of 404 errors to prevent frontend crashes
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StubController {

    /**
     * Stub endpoint for insider trades not available from the current provider.
     * Returns empty array to indicate no data available
     */
    @GetMapping("/insider-trades/{ticker}")
    public ResponseEntity<Object[]> getInsiderTrades(@PathVariable String ticker) {
        log.info("GET /api/v1/insider-trades/{} - STUB: Insider trades not available from provider", ticker);
        return ResponseEntity.ok(new Object[0]);
    }

    /**
     * Stub endpoint for analyst price targets not available from the current provider.
     * Returns empty array to indicate no data available
     */
    @GetMapping("/analyst-ratings/{ticker}/price-target")
    public ResponseEntity<Object[]> getPriceTarget(@PathVariable String ticker) {
        log.info(
            "GET /api/v1/analyst-ratings/{}/price-target - STUB: Price targets not available from provider",
            ticker
        );
        return ResponseEntity.ok(new Object[0]);
    }

    /**
     * Stub endpoint for analyst upgrades/downgrades not available from the current provider.
     * Returns empty array to indicate no data available
     */
    @GetMapping("/analyst-ratings/{ticker}/upgrades-downgrades")
    public ResponseEntity<Object[]> getUpgradesDowngrades(@PathVariable String ticker) {
        log.info(
            "GET /api/v1/analyst-ratings/{}/upgrades-downgrades - STUB: Upgrades/downgrades not available from provider",
            ticker
        );
        return ResponseEntity.ok(new Object[0]);
    }

}
