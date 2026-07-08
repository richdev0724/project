package com.marketmono.controller;

import com.marketmono.dto.StockInformationResponse;
import com.marketmono.service.StockInformationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for stock information endpoint
 * Provides detailed company and stock information
 * Endpoint: GET /api/v1/stock-information/{ticker}
 */
@RestController
@RequestMapping("/api/v1/stock-information")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class StockInformationController {

    private final StockInformationService stockInformationService;

    /**
     * Get detailed stock information (company name, sector, market cap, etc.)
     * 
     * @param ticker Stock ticker symbol
     * @return Stock information response with company and stock details
     * 
     * Example response:
     * {
     *   "data": {
     *     "announce_time": "unknown",
     *     "avg30_volume": "55973002",
     *     "full_name": "APPLE",
     *     "has_dividend": true,
     *     "has_earnings_history": true,
     *     "has_investment_arm": false,
     *     "has_options": true,
     *     "issue_type": "Common Stock",
     *     "marketcap": "2776014233920",
     *     "marketcap_size": "big",
     *     "next_earnings_date": "2023-10-26",
     *     "sector": "Technology",
     *     "short_description": "Apple Inc. is an American multinational technology company...",
     *     "symbol": "AAPL",
     *     "source_tags": []
     *   }
     * }
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<StockInformationResponse> getStockInformation(@PathVariable String ticker) {
        log.info("GET /api/v1/stock-information/{} - fetching stock information", ticker);

        try {
            StockInformationResponse response = stockInformationService.getStockInformation(ticker.toUpperCase());

            if (response != null && response.data() != null) {
                log.info("Returning stock information for {}: {} ({})", 
                         ticker, response.data().fullName(), response.data().sector());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No stock information available for {}", ticker);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error fetching stock information for ticker {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}


