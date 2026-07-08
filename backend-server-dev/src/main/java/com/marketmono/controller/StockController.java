package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockStateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for stock-related endpoints
 * Provides endpoints that mirror upstream market data API path structure
 */
@RestController
@RequestMapping("/api/stock")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class StockController {

    private final UnusualWhalesClient unusualWhalesClient;

    /**
     * Get stock state for a single ticker
     * Endpoint: /api/stock/{ticker}/stock-state
     * Mimics upstream market data API path structure
     *
     * @param ticker Stock ticker symbol
     * @return Stock state response with price, volume, and market data
     * 
     * Example response:
     * {
     *   "data": {
     *     "close": "56.79",
     *     "high": "56.79",
     *     "low": "56.79",
     *     "market_time": "postmarket",
     *     "open": "56.79",
     *     "prev_close": "55.69",
     *     "tape_time": "2023-09-07T20:11:00Z",
     *     "total_volume": 13774488,
     *     "volume": 500000
     *   }
     * }
     */
    @GetMapping("/{ticker}/stock-state")
    public ResponseEntity<StockStateResponse> getStockState(@PathVariable String ticker) {
        log.info("GET /api/stock/{}/stock-state", ticker);

        ticker = ticker.toUpperCase();
        StockStateResponse response = unusualWhalesClient.getStockState(ticker);

        log.debug("Returning stock state for {} - close={}", ticker, response.data().close());

        return ResponseEntity.ok(response);
    }
}





