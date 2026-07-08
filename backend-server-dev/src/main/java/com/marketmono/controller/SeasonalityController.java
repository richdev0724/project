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
 * REST controller for seasonality endpoint
 * Provides historical price change data by month/year
 * Endpoint: GET /api/v1/seasonality/{ticker}/year-month
 */
@RestController
@RequestMapping("/api/v1/seasonality")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class SeasonalityController {

    private final SeasonalityService seasonalityService;

    /**
     * Get price change per month data for a ticker (seasonality data)
     * Returns the relative price change for all past months over multiple years.
     * 
     * @param ticker Stock ticker symbol
     * @return Price change per month response with historical monthly data
     * 
     * Example response:
     * {
     *   "data": [
     *     {
     *       "change": "-0.0469",
     *       "close": 315.75,
     *       "month": 9,
     *       "open": 331.31,
     *       "year": 2023
     *     },
     *     {
     *       "change": "0.0690",
     *       "close": 338.11,
     *       "month": 10,
     *       "open": 316.28,
     *       "year": 2023
     *     }
     *   ]
     * }
     */
    @GetMapping("/{ticker}/year-month")
    public ResponseEntity<Map<String, Object>> getPriceChangePerMonth(@PathVariable String ticker) {
        log.info("GET /api/v1/seasonality/{}/year-month - fetching seasonality data", ticker);

        try {
            List<PriceChangePerMonthItemDto> data = seasonalityService.getPriceChangePerMonth(ticker.toUpperCase());

            if (data != null && !data.isEmpty()) {
                log.info("Returning {} seasonality data points for {}", data.size(), ticker);
                return ResponseEntity.ok(Map.of(
                    "data", data
                ));
            } else {
                log.warn("No seasonality data available for {}", ticker);
                return ResponseEntity.ok(Map.of(
                    "data", List.of()
                ));
            }
        } catch (Exception e) {
            log.error("Error fetching seasonality data for ticker {}", ticker, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage(),
                "data", List.of()
            ));
        }
    }
}



