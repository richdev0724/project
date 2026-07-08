package com.marketmono.controller;

import com.marketmono.dto.CongressTradesResponse;
import com.marketmono.service.PoliticianService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for congress trades
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class CongressTradesController {
    
    private final PoliticianService politicianService;
    
    /**
     * Get recent congress trades with optional filters
     * GET /api/v1/congress-recent-trades
     * 
     * @param date Filter by transaction date (YYYY-MM-DD)
     * @param limit Max trades to return (max: 200, will be capped if higher)
     * @param ticker Filter by ticker symbol
     * @return List of congress trades
     */
    @GetMapping("/congress-recent-trades")
    public ResponseEntity<CongressTradesResponse> getCongressRecentTrades(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String ticker) {
        
        log.info("GET /api/v1/congress-recent-trades - date={}, limit={}, ticker={}", 
                 date, limit, ticker);
        
        CongressTradesResponse response = politicianService.getCongressRecentTrades(date, limit, ticker);
        
        if (response == null || response.data() == null || response.data().isEmpty()) {
            log.warn("No congress trades available");
            return ResponseEntity.noContent().build();
        }
        
        log.info("Returning {} congress trades", response.data().size());
        return ResponseEntity.ok(response);
    }
}
