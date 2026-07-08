package com.marketmono.controller;

import com.marketmono.dto.EconomicCalendarResponse;
import com.marketmono.dto.MarketTideResponse;
import com.marketmono.dto.TopNetImpactResponse;
import com.marketmono.service.EconomicCalendarService;
import com.marketmono.service.MarketTideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for market tide endpoints
 * Provides market-wide options flow data with SPY price correlation
 */
@RestController
@CrossOrigin(origins = "*")
public class MarketTideController {
    
    private static final Logger log = LoggerFactory.getLogger(MarketTideController.class);
    
    private final MarketTideService marketTideService;
    private final EconomicCalendarService economicCalendarService;
    
    public MarketTideController(MarketTideService marketTideService, EconomicCalendarService economicCalendarService) {
        this.marketTideService = marketTideService;
        this.economicCalendarService = economicCalendarService;
    }
    
    /**
     * Get market tide data with optional date range
     * GET /api/market-tide?date=2024-01-15&endDate=2024-01-20
     * 
     * @param date Optional start date in YYYY-MM-DD format
     * @param endDate Optional end date in YYYY-MM-DD format
     * @return MarketTideResponse with time series data
     */
    @GetMapping("/api/v1/market-tide")
    public ResponseEntity<MarketTideResponse> getMarketTide(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String endDate) {
        
        log.debug("Market tide endpoint called - date: {}, endDate: {}", date, endDate);
        
        MarketTideResponse response = marketTideService.getMarketTide(date, endDate);
        
        if (!response.success()) {
            log.warn("Market tide service error");
            return ResponseEntity.internalServerError().body(response);
        }
        
        log.debug("Returning {} market tide data points", 
            response.data() != null ? response.data().size() : 0);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get top net impact tickers (bullish and bearish)
     * GET /api/top-net-impact
     * 
     * @return TopNetImpactResponse containing separated bullish and bearish ticker lists
     */
    @GetMapping("/api/v1/top-net-impact")
    public ResponseEntity<TopNetImpactResponse> getTopNetImpact() {
        log.debug("Top net impact endpoint called");
        
        TopNetImpactResponse response = marketTideService.getTopNetImpact();
        
        if (!response.success()) {
            log.warn("Top net impact service error: {}", response.error());
            return ResponseEntity.internalServerError().body(response);
        }
        
        log.debug("Returning {} bullish, {} bearish tickers", 
            response.data().bullish().size(), response.data().bearish().size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get economic calendar events with optional date range
     * GET /api/economic-calendar?startDate=2024-01-01&endDate=2024-01-07&days=7
     * 
     * @param startDate Optional start date in YYYY-MM-DD format
     * @param endDate Optional end date in YYYY-MM-DD format
     * @param days Number of days ahead to fetch (default 7)
     * @return EconomicCalendarResponse containing events grouped by date
     */
    @GetMapping("/api/v1/economic-calendar")
    public ResponseEntity<EconomicCalendarResponse> getEconomicCalendar(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(name = "start_date", required = false) String startDateSnake,
            @RequestParam(name = "end_date", required = false) String endDateSnake,
            @RequestParam(defaultValue = "7") int days) {
        
        log.debug("Economic calendar endpoint called - startDate: {}, endDate: {}, days: {}", 
            startDate, endDate, days);

        EconomicCalendarResponse response = economicCalendarService.getEconomicCalendar(
            startDate != null ? startDate : startDateSnake,
            endDate != null ? endDate : endDateSnake,
            days
        );
        
        if (!response.success()) {
            log.warn("Economic calendar service error: {}", response.error());
            return ResponseEntity.internalServerError().body(response);
        }
        
        log.debug("Returning {} days of economic events", 
            response.data() != null ? response.data().size() : 0);
        
        return ResponseEntity.ok(response);
    }
}
