package com.marketmono.controller;

import com.marketmono.dto.PriceChangePerMonthItemDto;
import com.marketmono.service.SeasonalityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for price change per month (seasonality) data from upstream market data provider
 * Endpoint: GET /api/v1/price-change-per-month/{ticker}
 */
@RestController
@RequestMapping("/api/v1/price-change-per-month")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PriceChangePerMonthController {
    
    private final SeasonalityService seasonalityService;
    
    /**
     * Get price change per month data for a ticker
     * Returns the relative price change for all past months over multiple years
     * 
     * @param ticker Stock ticker symbol
     * @return Price change per month data
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getPriceChangePerMonth(@PathVariable String ticker) {
        log.info("GET /api/v1/price-change-per-month/{}", ticker);
        
        try {
            List<PriceChangePerMonthItemDto> data = seasonalityService.getPriceChangePerMonth(ticker);
            
            log.info("Returning {} price change per month data points for {}", data.size(), ticker);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            log.error("Error fetching price change per month for {}", ticker, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}



