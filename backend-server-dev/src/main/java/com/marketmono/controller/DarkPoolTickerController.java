package com.marketmono.controller;

import com.marketmono.dto.DarkPoolRecentResponseDto;
import com.marketmono.service.DarkPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for dark pool ticker endpoints
 * Provides endpoints that mirror upstream market data API path structure
 */
@RestController
@RequestMapping("/api/darkpool")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class DarkPoolTickerController {

    private final DarkPoolService darkPoolService;

    /**
     * Get dark pool trades for a specific ticker
     * Endpoint: /api/darkpool/{ticker}
     * Mimics upstream market data API path structure
     *
     * Returns the dark pool trades for the given ticker on a given day.
     * Date must be the current or a past date. If no date is given, returns data for the current/last market day.
     *
     * @param ticker Stock ticker symbol
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of results (default: 200, max: 200, min: 1)
     * @param maxPremium Maximum premium filter
     * @param minPremium Minimum premium filter
     * @param newerThan Timestamp filter - return trades newer than this timestamp (ISO 8601 format)
     * @return Dark pool trades response for the ticker
     * 
     * Example response:
     * {
     *   "data": [
     *     {
     *       "canceled": false,
     *       "executed_at": "2023-02-16T00:59:44Z",
     *       "ext_hour_sold_codes": "extended_hours_trade",
     *       "market_center": "L",
     *       "nbbo_ask": "19",
     *       "nbbo_ask_quantity": 6600,
     *       "nbbo_bid": "18.99",
     *       "nbbo_bid_quantity": 29100,
     *       "premium": "121538.56",
     *       "price": "18.9904",
     *       "sale_cond_codes": null,
     *       "size": 6400,
     *       "ticker": "QID",
     *       "tracking_id": 71984388012245,
     *       "trade_code": null,
     *       "trade_settlement": "regular_settlement",
     *       "volume": 9946819
     *     }
     *   ]
     * }
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<DarkPoolRecentResponseDto> getDarkPoolTrades(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "200") Integer limit,
            @RequestParam(name = "max_premium", required = false) Long maxPremium,
            @RequestParam(name = "min_premium", required = false) Long minPremium,
            @RequestParam(name = "newer_than", required = false) String newerThan
    ) {
        log.info("GET /api/darkpool/{} - date: {}, limit: {}, maxPremium: {}, minPremium: {}, newerThan: {}", 
                 ticker, date, limit, maxPremium, minPremium, newerThan);

        try {
            ticker = ticker.toUpperCase();
            DarkPoolRecentResponseDto response = darkPoolService.getDarkPoolTradesByTicker(
                ticker, date, limit, maxPremium, minPremium, newerThan
            );

            if (response != null && response.data() != null) {
                log.info("Returning {} dark pool trades for ticker {}", response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No dark pool trades available for ticker {}", ticker);
                return ResponseEntity.ok(new DarkPoolRecentResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching dark pool trades for ticker {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

