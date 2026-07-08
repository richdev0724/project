package com.marketmono.controller;

import com.marketmono.dto.EtfHoldingsResponseDto;
import com.marketmono.service.EtfHoldingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for ETF holdings data
 * Endpoint: GET /api/v1/etfs/{ticker}/holdings
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EtfHoldingsController {

    private final EtfHoldingsService etfHoldingsService;

    /**
     * Get ETF holdings for a given ticker
     *
     * @param ticker ETF ticker symbol (e.g. "SPY", "QQQ")
     * @param date   Optional date in YYYY-MM-DD format
     * @param limit  Number of results (default: 500, max: 500)
     * @param page   Page number (starts at 0)
     * @return ETF holdings response
     */
    @GetMapping("/etfs/{ticker}/holdings")
    public ResponseEntity<EtfHoldingsResponseDto> getEtfHoldings(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "500") Integer limit,
            @RequestParam(required = false) Integer page
    ) {
        log.info("GET /api/v1/etfs/{}/holdings - date: {}, limit: {}, page: {}", ticker, date, limit, page);

        try {
            ticker = ticker.toUpperCase();

            EtfHoldingsResponseDto response = etfHoldingsService.getEtfHoldings(ticker, date, limit, page);

            if (response != null && response.data() != null) {
                log.info("Returning {} ETF holdings for {}", response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No ETF holdings data available for {}", ticker);
                return ResponseEntity.ok(new EtfHoldingsResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching ETF holdings for {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
