package com.marketmono.controller;

import com.marketmono.dto.OffLitPriceLevelDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.StockVolumePriceLevelsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for stock volume price levels (dark pool & lit pool data)
 * Endpoints:
 *   GET /api/v1/volume-price-levels/{ticker}
 */
@RestController
@RequestMapping("/api/v1/volume-price-levels")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StockVolumePriceLevelsController {
    
    private final StockVolumePriceLevelsService volumePriceLevelsService;
    
    /**
     * Get stock volume price levels (dark & lit pool data) for a ticker
     * Returns aggregated volume by price level for both off-lit (dark pool) and lit (exchange) trades
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional date in YYYY-MM-DD format (defaults to current/last market day)
     * @return Volume price levels data with dark pool and lit pool volumes
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getVolumePriceLevels(
            @PathVariable String ticker,
            @RequestParam(required = false) String date
    ) {
        log.info("GET /api/v1/volume-price-levels/{} - date: {}", ticker, date);
        
        try {
            List<OffLitPriceLevelDto> data = 
                volumePriceLevelsService.getVolumePriceLevels(ticker, date);
            
            // Calculate summary statistics
            long totalDarkPool = volumePriceLevelsService.calculateTotalDarkPoolVolume(data);
            long totalLit = volumePriceLevelsService.calculateTotalLitVolume(data);
            double darkPoolPct = volumePriceLevelsService.calculateDarkPoolPercentage(data);
            
            log.info("Returning {} volume price levels for {} (DP: {}, Lit: {})", 
                data.size(), ticker, totalDarkPool, totalLit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data,
                "summary", Map.of(
                    "totalDarkPoolVolume", totalDarkPool,
                    "totalLitVolume", totalLit,
                    "totalVolume", totalDarkPool + totalLit,
                    "darkPoolPercentage", String.format("%.2f", darkPoolPct)
                )
            ));
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching volume price levels for {}", ticker, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}



