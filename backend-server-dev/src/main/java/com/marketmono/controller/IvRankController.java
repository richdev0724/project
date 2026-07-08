package com.marketmono.controller;

import com.marketmono.dto.IvRankDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.IvRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for IV Rank data from upstream market data provider
 * Endpoint: GET /api/v1/iv-rank/{ticker}
 * 
 * IV rank is a measure of where current implied volatility stands relative to its historical range
 */
@RestController
@RequestMapping("/api/v1/iv-rank")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class IvRankController {
    
    private final IvRankService ivRankService;
    
    /**
     * Get IV Rank data for a ticker over a time period
     * 
     * @param ticker Stock ticker symbol
     * @param date Trading date (optional, defaults to last trading date, format: YYYY-MM-DD)
     * @param timespan Optional timespan ('1y', '6m', '3m', '1m', '1w' or ISO date like '2024-01-25')
     * @return IV rank data points containing date, close, volatility, and iv_rank_1y
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getIvRank(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String timespan
    ) {
        log.info("GET /api/v1/iv-rank/{} - date: {}, timespan: {}", 
                 ticker, date, timespan);
        
        try {
            List<IvRankDto> data = ivRankService.getIvRank(ticker, date, timespan);
            
            log.info("Returning {} IV rank data points for {}", data.size(), ticker);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching IV rank for {}", ticker, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
