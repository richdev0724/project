package com.marketmono.controller;

import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.OhlcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for OHLC (Open, High, Low, Close) candle data endpoints
 */
@RestController
@RequestMapping("/api/v1/ohlc")
@CrossOrigin(origins = "*")
public class OhlcController {
    
    private static final Logger log = LoggerFactory.getLogger(OhlcController.class);
    
    private final OhlcService ohlcService;
    
    public OhlcController(OhlcService ohlcService) {
        this.ohlcService = ohlcService;
    }
    
    /**
     * Get OHLC candle data for a specific ticker and candle size
     * Endpoint: GET /api/v1/ohlc/{ticker}/{candleSize}
     * 
     * @param ticker Stock ticker symbol (e.g., AAPL, TSLA)
     * @param candleSize Candle size: 1m, 5m, 10m, 15m, 30m, 1h, 4h, 1d
     * @param date Optional date in YYYY-MM-DD format for historical data
     * @param timeframe Optional timeframe (1D, 2D, 1W, 2W, 1M, 2M, 1Y, 2Y, YTD)
     * @param endDate Optional end date in YYYY-MM-DD format (results go backwards from this date)
     * @param limit Optional limit for number of candles (max 2500, min 1)
     * @return UnusualWhalesOhlcDto containing OHLC candle data
     */
    @GetMapping("/{ticker}/{candleSize}")
    public ResponseEntity<UnusualWhalesOhlcDto> getOhlcData(
            @PathVariable String ticker,
            @PathVariable String candleSize,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String timeframe,
            @RequestParam(name = "end_date", required = false) String endDate,
            @RequestParam(required = false) Integer limit) {
        
        log.debug("Received OHLC request: ticker={}, candleSize={}, date={}, timeframe={}, endDate={}, limit={}", 
                  ticker, candleSize, date, timeframe, endDate, limit);
        
        try {
            // Validate candle size
            if (!isValidCandleSize(candleSize)) {
                log.warn("Invalid candle size: {}", candleSize);
                return ResponseEntity.badRequest().build();
            }
            
            // Validate limit if provided
            if (limit != null && (limit < 1 || limit > 2500)) {
                log.warn("Invalid limit: {} (must be between 1 and 2500)", limit);
                return ResponseEntity.badRequest().build();
            }
            
            UnusualWhalesOhlcDto response = ohlcService.getOhlcData(
                ticker.toUpperCase(), 
                candleSize, 
                date, 
                timeframe, 
                endDate, 
                limit
            );
            
            if (response == null || response.data() == null || response.data().isEmpty()) {
                log.warn("No OHLC data available for {} with {} candle size", ticker, candleSize);
                return ResponseEntity.noContent().build();
            }
            
            log.debug("Returning {} OHLC candles for {}", response.data().size(), ticker);
            return ResponseEntity.ok(response);
            
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for OHLC data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing OHLC request for {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Validate candle size parameter
     * Valid values: 1m, 5m, 10m, 15m, 30m, 1h, 4h, 1d
     */
    private boolean isValidCandleSize(String candleSize) {
        return candleSize != null && candleSize.matches("^(1m|5m|10m|15m|30m|1h|4h|1d)$");
    }
}
