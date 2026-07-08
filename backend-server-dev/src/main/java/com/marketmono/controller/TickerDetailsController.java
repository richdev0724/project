package com.marketmono.controller;

import com.marketmono.dto.TickerDetailsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * STUB REST controller for ticker details endpoint
 * 
 * NOTE: This is a minimal stub implementation.
 * The current market data provider does not provide company metadata endpoints.
 * Returns stub response with null fields for missing data.
 * 
 * For full company metadata (name, logo, description, market cap, etc.),
 * an external data source would be required (e.g., FMP API, Yahoo Finance).
 */
@RestController
@RequestMapping("/api/v1/ticker-details")
@CrossOrigin(origins = "*")
@Slf4j
public class TickerDetailsController {

    /**
     * Get ticker details (STUB - returns minimal data)
     * Replaces Polygon /v3/reference/tickers/{ticker} endpoint
     *
     * @param ticker Stock ticker symbol
     * @return Stub ticker details response with null fields
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<TickerDetailsResponse> getTickerDetails(@PathVariable String ticker) {
        log.info("GET /api/v1/ticker-details/{} - STUB endpoint (provider limitation)", ticker);

        ticker = ticker.toUpperCase();
        
        TickerDetailsResponse response = TickerDetailsResponse.createStub(ticker);
        
        log.debug("Returning stub ticker details for {} - company metadata not available", ticker);

        return ResponseEntity.ok(response);
    }
}
