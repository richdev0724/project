package com.marketmono.controller;

import com.marketmono.dto.TickerTideResponse;
import com.marketmono.service.TickerTideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ticker-tide")
@CrossOrigin(origins = "*")
public class TickerTideController {
    
    private static final Logger log = LoggerFactory.getLogger(TickerTideController.class);
    
    private final TickerTideService tickerTideService;
    
    public TickerTideController(TickerTideService tickerTideService) {
        this.tickerTideService = tickerTideService;
    }
    
    /**
     * Get ticker-specific tide data (net premiums, volume, ticker price over time)
     * 
     * @param ticker Stock ticker symbol (path variable)
     * @param date Optional start date in YYYY-MM-DD format
     * @param endDate Optional end date in YYYY-MM-DD format
     * @return TickerTideResponse with time-series data
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<TickerTideResponse> getTickerTide(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String endDate) {
        
        log.debug("Received request for ticker tide: ticker={}, date={}, endDate={}", ticker, date, endDate);
        
        TickerTideResponse response = tickerTideService.getTickerTide(ticker, date, endDate);
        
        if (response.success()) {
            log.debug("Returning {} data points for {}", 
                response.data() != null ? response.data().size() : 0, ticker);
            return ResponseEntity.ok(response);
        } else {
            log.warn("Failed to fetch ticker tide data for {}", ticker);
            return ResponseEntity.badRequest().body(response);
        }
    }
}
