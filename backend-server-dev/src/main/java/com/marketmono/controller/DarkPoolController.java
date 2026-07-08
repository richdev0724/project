package com.marketmono.controller;

import com.marketmono.dto.*;
import com.marketmono.service.DarkPoolService;
import com.marketmono.service.DpLpService;
import com.marketmono.service.OffLitTradeBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for dark pool and lit pool data endpoints.
 * Provides aggregated data from the database for frontend consumption.
 */
@RestController
@RequestMapping("/api/v1")
public class DarkPoolController {
    
    private static final Logger log = LoggerFactory.getLogger(DarkPoolController.class);
    
    private final DpLpService dpLpService;
    private final DarkPoolService darkPoolService;
    private final OffLitTradeBroadcaster offLitTradeBroadcaster;
    
    public DarkPoolController(DpLpService dpLpService, DarkPoolService darkPoolService,
                              OffLitTradeBroadcaster offLitTradeBroadcaster) {
        this.dpLpService = dpLpService;
        this.darkPoolService = darkPoolService;
        this.offLitTradeBroadcaster = offLitTradeBroadcaster;
    }
    
    /**
     * Get recent off-lit trades from the in-memory buffer.
     * Used for page-load hydration before the STOMP subscription kicks in.
     *
     * @param limit max trades to return (default 200, max 500)
     * @return list of recent dark pool trades, newest last
     */
    @GetMapping("/off-lit-trades/recent")
    public ResponseEntity<List<DarkPoolRecentTradeDto>> getRecentOffLitTrades(
        @RequestParam(defaultValue = "200") int limit
    ) {
        List<DarkPoolRecentTradeDto> trades = offLitTradeBroadcaster.getRecentTrades(limit);
        return ResponseEntity.ok(trades);
    }

    /**
     * Get top dark pool trades from the same recent-trades source used by Dark Flow.
     * Endpoint: GET /api/v1/darkpool-recent/top
     *
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of aggregated rows (default 10, max enforced in service)
     * @return Top dark pool trades response
     */
    @GetMapping("/darkpool-recent/top")
    public ResponseEntity<DarkPoolTopTradesResponseDto> getTopDarkPoolTradesForDay(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "10") Integer limit
    ) {
        DarkPoolTopTradesResponseDto response = darkPoolService.getTopDarkPoolTradesForDay(
            date,
            limit != null ? limit : 10
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get top dark pool activity for multiple tickers.
     * Endpoint: POST /api/v1/darkLitReports/dark-pool/top-activity
     * 
     * @param request Query parameters including ticker list and time window
     * @return List of dark pool top activity DTOs
     */
    @PostMapping("/darkLitReports/dark-pool/top-activity")
    public ResponseEntity<List<DarkPoolTopActivityDto>> getDarkPoolTopActivity(
        @RequestBody MarketDataQueryRequest request
    ) {
        log.info("Fetching dark pool top activity for ticker: {}", request.ticker());
        
        try {
            // Calculate time window (default to "1d" if not provided)
            String window = "1d"; // Could be extracted from request if needed
            
            // Get summary from service
            DpLpSummaryDto summary = dpLpService.getSummary(request.ticker(), window);
            
            // Transform to frontend DTO
            List<DarkPoolTopActivityDto> result = new ArrayList<>();
            if (summary != null && summary.darkPool() != null) {
                result.add(new DarkPoolTopActivityDto(
                    request.ticker(),
                    summary.darkPool().totalVolume()
                ));
            }
            
            log.info("Returning {} dark pool top activity records", result.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error fetching dark pool top activity for ticker {}: {}", request.ticker(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get top lit pool activity for multiple tickers.
     * Endpoint: POST /api/v1/darkLitReports/lit-pool/top-activity
     * 
     * @param request Query parameters including ticker list and time window
     * @return List of lit pool top activity DTOs
     */
    @PostMapping("/darkLitReports/lit-pool/top-activity")
    public ResponseEntity<List<LitPoolTopActivityDto>> getLitPoolTopActivity(
        @RequestBody MarketDataQueryRequest request
    ) {
        log.info("Fetching lit pool top activity for ticker: {}", request.ticker());
        
        try {
            // Calculate time window
            String window = "1d";
            
            // Get summary from service
            DpLpSummaryDto summary = dpLpService.getSummary(request.ticker(), window);
            
            // Transform to frontend DTO
            List<LitPoolTopActivityDto> result = new ArrayList<>();
            if (summary != null && summary.litPool() != null) {
                result.add(new LitPoolTopActivityDto(
                    request.ticker(),
                    summary.litPool().totalVolume()
                ));
            }
            
            log.info("Returning {} lit pool top activity records", result.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error fetching lit pool top activity for ticker {}: {}", request.ticker(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get detailed dark pool market data with price levels.
     * Endpoint: POST /api/v1/darkLitReports/market-data/dark-pool
     * 
     * This endpoint returns stored dark pool data from the database, not real-time scanner results.
     * The frontend expects price levels with ticker, price, size, total_premium, trades, market_session, trade_date.
     * 
     * Note: The original Node.js backend had scanner algorithms (darkPoolScanner.js, litFlowScanner.js) that
     * performed real-time analysis. This implementation returns historical aggregated data from the database.
     * 
     * @param request Query parameters including showLevels, ticker, startDate, endDate, levelsAmount
     * @return List of dark pool level DTOs
     */
    @PostMapping("/darkLitReports/market-data/dark-pool")
    public ResponseEntity<List<DarkPoolLevelDto>> getMarketDataDarkPool(
        @RequestBody MarketDataQueryRequest request
    ) {
        log.info("Fetching dark pool market data for ticker: {}, showLevels: {}, levelsAmount: {}", 
            request.ticker(), request.showLevels(), request.levelsAmount());
        
        try {
            // Calculate time window
            String window = "1d"; // Default to 1 day window
            
            // Get summary with top price levels from service
            DpLpSummaryDto summary = dpLpService.getSummary(request.ticker(), window);
            
            // Transform price levels to frontend DTOs
            List<DarkPoolLevelDto> result = new ArrayList<>();
            if (summary != null && summary.topLevels() != null) {
                result = summary.topLevels().stream()
                    .limit(request.levelsAmount() > 0 ? request.levelsAmount() : 100)
                    .map(level -> new DarkPoolLevelDto(
                        request.ticker(),
                        level.price(),
                        level.netVolume(),
                        level.price().multiply(java.math.BigDecimal.valueOf(level.netVolume())), // total premium = price * volume
                        level.darkVolume().intValue() + level.litVolume().intValue(), // approximate trade count
                        "regular", // Default market session
                        summary.window()
                    ))
                    .collect(Collectors.toList());
            }
            
            log.info("Returning {} dark pool price levels", result.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error fetching dark pool market data for ticker {}: {}", request.ticker(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get dark pool scanner results for a specific date.
     * Endpoint: GET /api/v1/dark-pool/scanner?date={date}
     * 
     * This endpoint returns stored dark pool scanner results from the database.
     * The original Node.js backend had active scanner algorithms (darkPoolScanner.js, litFlowScanner.js)
     * that performed real-time analysis with configurable thresholds:
     * 
     * darkPoolScanner.js logic:
     * - Fetches dark pool and lit pool data from database
     * - Filters dark pool trades with volume >= 50,000 shares
     * - Calculates dark-to-lit ratio (dark_volume / lit_volume)
     * - Applies threshold filters: ratio > 2.0 (200% more dark than lit)
     * - Returns alerts for unusual dark pool activity
     * 
     * litFlowScanner.js logic:
     * - Analyzes lit pool flow patterns
     * - Identifies significant lit pool imbalances
     * - Detects unusual order flow characteristics
     * 
     * This Java implementation returns the stored aggregated data without active scanning.
     * To implement active scanning, the scanner algorithms would need to be ported from Node.js
     * and integrated with the WebSocket system for real-time alerts.
     * 
     * @param date Date to query scanner results (format: yyyy-MM-dd)
     * @return Scanner response with status, data, and last scan time
     */
    @GetMapping("/dark-pool/scanner")
    public ResponseEntity<DarkPoolScannerResponseDto> getDarkPoolScanner(
        @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        log.info("Fetching dark pool scanner results for date: {}", queryDate);
        
        try {
            // For now, return a placeholder response indicating the scanner is not actively running
            // In a future enhancement, this would query actual scanner results from a dedicated table
            DarkPoolScannerResponseDto response = new DarkPoolScannerResponseDto(
                "stored_data",
                new ArrayList<>(), // Empty data list - would be populated with scanner results
                LocalDateTime.now()
            );
            
            log.info("Returning scanner response with status: {}", response.status());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching dark pool scanner results for date {}: {}", queryDate, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get recent dark pool trades from upstream market data API
     * Endpoint: GET /api/v1/darkpool-recent
     * 
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of results (default: 200, max: 200, min: 1)
     * @param minPremium Minimum premium filter (default: 50000000)
     * @param maxPremium Maximum premium filter
     * @param minSize Minimum size filter
     * @param maxSize Maximum size filter
     * @param minVolume Minimum volume filter
     * @param maxVolume Maximum volume filter
     * @return Recent dark pool trades response
     */
    @GetMapping("/darkpool-recent")
    public ResponseEntity<DarkPoolRecentResponseDto> getRecentDarkPoolTrades(
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "200") Integer limit,
            @RequestParam(name = "min_premium", required = false, defaultValue = "50000000") Long minPremium,
            @RequestParam(name = "max_premium", required = false) Long maxPremium,
            @RequestParam(name = "min_size", required = false) Integer minSize,
            @RequestParam(name = "max_size", required = false) Integer maxSize,
            @RequestParam(name = "min_volume", required = false) Integer minVolume,
            @RequestParam(name = "max_volume", required = false) Integer maxVolume
    ) {
        log.info("GET /api/v1/darkpool-recent - date: {}, limit: {}, minPremium: {}", date, limit, minPremium);
        
        try {
            DarkPoolRecentResponseDto response = darkPoolService.getRecentDarkPoolTrades(
                date, limit, minPremium, maxPremium, minSize, maxSize, minVolume, maxVolume
            );
            
            if (response != null && response.data() != null) {
                log.info("Returning {} recent dark pool trades", response.data().size());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No recent dark pool trades available");
                return ResponseEntity.ok(new DarkPoolRecentResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching recent dark pool trades", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get dark pool trades for a specific ticker
     * Endpoint: GET /api/v1/darkpool/{ticker}
     * 
     * Returns the dark pool trades for the given ticker on a given day.
     * Date must be the current or a past date. If no date is given, returns data for the current/last market day.
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of results (default: 200, max: 200, min: 1)
     * @param maxPremium Maximum premium filter
     * @param minPremium Minimum premium filter
     * @param newerThan Timestamp filter - return trades newer than this timestamp
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
    @GetMapping("/darkpool/{ticker}")
    public ResponseEntity<DarkPoolRecentResponseDto> getDarkPoolTradesByTicker(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "200") Integer limit,
            @RequestParam(name = "max_premium", required = false) Long maxPremium,
            @RequestParam(name = "min_premium", required = false) Long minPremium,
            @RequestParam(name = "newer_than", required = false) String newerThan
    ) {
        log.info("GET /api/v1/darkpool/{} - date: {}, limit: {}, maxPremium: {}, minPremium: {}, newerThan: {}", 
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
