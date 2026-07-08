package com.marketmono.controller;

import com.marketmono.dto.EarningsByDateResponseDto;
import com.marketmono.dto.EarningsDetailedItemDto;
import com.marketmono.dto.TickerEarningsResponseDto;
import com.marketmono.service.EarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for earnings data from upstream market data provider
 * Endpoints: GET /api/v1/earnings/{date}, GET /api/v1/earnings/ticker/{ticker}
 */
@RestController
@RequestMapping("/api/v1/earnings")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EarningsController {

    private final EarningsService earningsService;

    /**
     * Get earnings data for a specific date (premarket and afterhours combined)
     *
     * @param date Date in YYYY-MM-DD format
     * @return Earnings by date response containing both premarket and afterhours
     */
    @GetMapping("/{date}")
    public ResponseEntity<EarningsByDateResponseDto> getEarningsByDate(@PathVariable String date) {
        log.info("GET /api/v1/earnings/{} - fetching earnings by date", date);

        try {
            EarningsByDateResponseDto response = earningsService.getEarningsByDate(date);

            if (response != null) {
                log.info("Returning earnings for {}: {} premarket, {} afterhours",
                    date, response.premarket().size(), response.afterhours().size());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No earnings data available for {}", date);
                return ResponseEntity.ok(new EarningsByDateResponseDto(date, java.util.List.of(), java.util.List.of(), false));
            }
        } catch (Exception e) {
            log.error("Error fetching earnings for date {}", date, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get earnings data for a specific ticker
     *
     * @param ticker Stock ticker symbol
     * @return Ticker earnings response containing earnings data
     */
    @GetMapping("/ticker/{ticker}")
    public ResponseEntity<TickerEarningsResponseDto> getTickerEarnings(@PathVariable String ticker) {
        log.info("GET /api/v1/earnings/ticker/{} - fetching ticker earnings", ticker);

        try {
            TickerEarningsResponseDto response = earningsService.getTickerEarnings(ticker.toUpperCase());

            if (response != null && response.data() != null) {
                log.info("Returning {} earnings data points for {}", response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No earnings data available for {}", ticker);
                return ResponseEntity.ok(new TickerEarningsResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching earnings for ticker {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get detailed premarket earnings data with optional date, limit, and pagination
     * 
     * @param date Date in YYYY-MM-DD format (optional, defaults to today)
     * @param limit Number of results (default: 50, max: 100, min: 1)
     * @param page Page number for pagination (optional)
     * @return Detailed premarket earnings data
     */
    @GetMapping("/premarket")
    public ResponseEntity<Map<String, Object>> getPremarketEarnings(
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) Integer page) {
        log.info("GET /api/v1/earnings/premarket - date: {}, limit: {}, page: {}", date, limit, page);

        try {
            List<EarningsDetailedItemDto> data = earningsService.getPremarketEarningsDetailed(date, limit, page);
            
            log.info("Returning {} premarket earnings for {}", data.size(), date != null ? date : "today");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            log.error("Error fetching premarket earnings - date: {}, limit: {}, page: {}", date, limit, page, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get detailed afterhours earnings data with optional date, limit, and pagination
     * 
     * @param date Date in YYYY-MM-DD format (optional, defaults to today)
     * @param limit Number of results (default: 50, max: 100, min: 1)
     * @param page Page number for pagination (optional)
     * @return Detailed afterhours earnings data
     */
    @GetMapping("/afterhours")
    public ResponseEntity<Map<String, Object>> getAfterhoursEarnings(
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) Integer page) {
        log.info("GET /api/v1/earnings/afterhours - date: {}, limit: {}, page: {}", date, limit, page);

        try {
            List<EarningsDetailedItemDto> data = earningsService.getAfterhoursEarningsDetailed(date, limit, page);
            
            log.info("Returning {} afterhours earnings for {}", data.size(), date != null ? date : "today");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            log.error("Error fetching afterhours earnings - date: {}, limit: {}, page: {}", date, limit, page, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}