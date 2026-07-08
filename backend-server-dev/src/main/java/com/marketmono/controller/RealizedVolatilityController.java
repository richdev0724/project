package com.marketmono.controller;

import com.marketmono.dto.RealizedVolatilityDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.RealizedVolatilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Realized Volatility data from upstream market data provider
 * Endpoint: GET /api/v1/realized-volatility/{ticker}
 * 
 * Compares implied volatility (IV) with realized volatility (RV) to understand
 * whether options are overpriced or underpriced relative to actual price movement
 */
@RestController
@RequestMapping("/api/v1/realized-volatility")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RealizedVolatilityController {
    
    private final RealizedVolatilityService realizedVolatilityService;
    
    /**
     * Get Realized Volatility data for a ticker over a time period
     * 
     * @param ticker Stock ticker symbol
     * @param date Trading date (optional, defaults to last trading date, format: YYYY-MM-DD)
     * @param timeframe Optional timeframe ('YTD', '1D', '2D', '1W', '2W', '1M', '2M', '1Y', '2Y', default: '1Y')
     * @return Realized volatility data points with date, price, implied_volatility, realized_volatility, and unshifted_rv_date
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getRealizedVolatility(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "1Y") String timeframe
    ) {
        log.info("GET /api/v1/realized-volatility/{} - date: {}, timeframe: {}", 
                 ticker, date, timeframe);
        
        try {
            List<RealizedVolatilityDto> data = 
                realizedVolatilityService.getRealizedVolatility(ticker, date, timeframe);
            
            log.info("Returning {} realized volatility data points for {}", data.size(), ticker);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching realized volatility for {}", ticker, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
