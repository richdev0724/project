package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockPriceLevelsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for options price levels endpoint
 * Provides call and put volume distribution across price levels
 * Endpoint: GET /api/v1/options-price-levels/{ticker}
 */
@RestController
@RequestMapping("/api/v1/options-price-levels")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StockPriceLevelsController {
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Get options price levels with call and put volume
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date in YYYY-MM-DD format (optional, defaults to most recent trading day)
     * @return Options price levels with volume distribution
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<StockPriceLevelsResponseDto> getStockPriceLevels(
            @PathVariable String ticker,
            @RequestParam(required = false) String date
    ) {
        log.info("GET /api/v1/options-price-levels/{} - date: {}", ticker, date);
        
        try {
            StockPriceLevelsResponseDto response = 
                unusualWhalesClient.getStockPriceLevels(ticker, date);
            
            if (response != null && response.data() != null) {
                log.info("Returning {} options price levels for {}", 
                    response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No options price levels data available for {}", ticker);
                return ResponseEntity.ok(new StockPriceLevelsResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching options price levels for {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
