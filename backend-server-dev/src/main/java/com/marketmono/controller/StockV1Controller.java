package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockMaxPainResponse;
import com.marketmono.dto.StockStateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for stock endpoints under /api/v1/stock
 * Provides versioned API endpoints for stock-related data
 */
@RestController
@RequestMapping("/api/v1/stock")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class StockV1Controller {

    private final UnusualWhalesClient unusualWhalesClient;

    /**
     * Get stock state for a single ticker
     * Endpoint: /api/v1/stock/{ticker}/stock-state
     *
     * @param ticker Stock ticker symbol
     * @return Stock state response with price, volume, and market data
     */
    @GetMapping("/{ticker}/stock-state")
    public ResponseEntity<StockStateResponse> getStockState(@PathVariable String ticker) {
        log.info("GET /api/v1/stock/{}/stock-state", ticker);

        ticker = ticker.toUpperCase();
        StockStateResponse response = unusualWhalesClient.getStockState(ticker);

        log.debug("Returning stock state for {} - close={}", ticker, response.data().close());

        return ResponseEntity.ok(response);
    }

    /**
     * Get stock max pain by expiry for a single ticker.
     * Endpoint: /api/v1/stock/{ticker}/max-pain
     *
     * @param ticker Stock ticker symbol
     * @param date Optional market date in YYYY-MM-DD format
     * @return Max pain response with expiry rows and optional date marker
     */
    @GetMapping("/{ticker}/max-pain")
    public ResponseEntity<StockMaxPainResponse> getStockMaxPain(
        @PathVariable String ticker,
        @RequestParam(required = false) String date
    ) {
        log.info("GET /api/v1/stock/{}/max-pain?date={}", ticker, date);

        ticker = ticker.toUpperCase();
        StockMaxPainResponse response = unusualWhalesClient.getStockMaxPain(ticker, date);

        log.debug("Returning max pain for {} - rows={}", ticker, response.data() != null ? response.data().size() : 0);

        return ResponseEntity.ok(response);
    }
}



