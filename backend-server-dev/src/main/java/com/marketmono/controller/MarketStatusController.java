package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.MarketStatusResponse;
import com.marketmono.dto.StockStateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * REST controller for market status endpoint
 * Provides market open/closed status using upstream market data API stock-state data
 */
@RestController
@RequestMapping("/api/v1/market-status")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class MarketStatusController {

    private final UnusualWhalesClient unusualWhalesClient;
    
    private static final String DEFAULT_TICKER = "SPY"; // S&P 500 ETF as market proxy

    /**
     * Get current market status (open/closed/extended-hours)
     * Uses SPY stock-state to determine market session
     *
     * @return Market status response with exchange statuses
     */
    @GetMapping
    public ResponseEntity<MarketStatusResponse> getMarketStatus() {
        log.info("GET /api/v1/market-status - fetching via {}", DEFAULT_TICKER);

        try {
            // Use SPY stock-state to determine market status
            StockStateResponse stockState = unusualWhalesClient.getStockState(DEFAULT_TICKER);
            
            if (stockState == null || stockState.data() == null) {
                log.warn("No stock state data returned for {}", DEFAULT_TICKER);
                return ResponseEntity.status(503).build();
            }
            
            String marketTime = stockState.data().marketTime();
            String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
            
            MarketStatusResponse response = MarketStatusResponse.fromMarketTime(marketTime, timestamp);
            
            log.debug("Market status: {} (market_time={})", response.exchanges().nasdaq(), marketTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to fetch market status: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
