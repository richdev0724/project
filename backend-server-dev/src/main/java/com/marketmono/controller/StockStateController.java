package com.marketmono.controller;

import com.marketmono.dto.StockStateResponse;
import com.marketmono.service.StockStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for stock state endpoint
 * Provides current stock price, volume, and market time data
 * Endpoint: GET /api/v1/stock-state/{ticker}
 */
@RestController
@RequestMapping("/api/v1/stock-state")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class StockStateController {

    private final StockStateService stockStateService;

    /**
     * Get current stock state (price, volume, prev close, market time) for a ticker
     * 
     * @param ticker Stock ticker symbol
     * @return Stock state response with current price and volume data
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
    @GetMapping("/{ticker}")
    public ResponseEntity<StockStateResponse> getStockState(@PathVariable String ticker) {
        log.info("GET /api/v1/stock-state/{} - fetching stock state", ticker);

        try {
            StockStateResponse response = stockStateService.getStockState(ticker.toUpperCase());

            if (response != null && response.data() != null) {
                log.info("Returning stock state for {}: close=${}, volume={}", 
                         ticker, response.data().close(), response.data().totalVolume());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No stock state data available for {}", ticker);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error fetching stock state for ticker {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}



