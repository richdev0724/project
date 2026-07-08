package com.marketmono.controller;

import com.marketmono.dto.TickerEarningsResponseDto;
import com.marketmono.service.EarningsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authenticated historical ticker earnings endpoint
 * Requires JWT authentication
 * Endpoint: GET /api/historicalEarnings/{ticker}
 */
@RestController
@RequestMapping("/api/historicalEarnings")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class HistoricalTickerEarningsController {

    private final EarningsService earningsService;

    /**
     * Get historical earnings data for a specific ticker
     * This endpoint requires JWT authentication
     *
     * @param ticker Stock ticker symbol
     * @return Ticker earnings response containing historical earnings data
     * 
     * Example response:
     * {
     *   "data": [
     *     {
     *       "actual_eps": "2.45",
     *       "ending_fiscal_quarter": "2024-09-30",
     *       "expected_move": "9.91",
     *       "expected_move_perc": "0.0359",
     *       "long_straddle_1d": "0.2349",
     *       "long_straddle_1w": "0.0129",
     *       "post_earnings_move_1d": "0.0724",
     *       "post_earnings_move_1w": "0.132",
     *       "post_earnings_move_2w": "0.1582",
     *       "post_earnings_move_3d": "0.0231",
     *       "pre_earnings_move_1d": "0.0724",
     *       "pre_earnings_move_1w": "0.132",
     *       "pre_earnings_move_2w": "0.1582",
     *       "pre_earnings_move_3d": "0.0231",
     *       "report_date": "2024-11-10",
     *       "report_time": "postmarket",
     *       "short_straddle_1d": "-0.5830",
     *       "short_straddle_1w": "-0.005",
     *       "source": "company",
     *       "street_mean_est": "2.25"
     *     }
     *   ]
     * }
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<TickerEarningsResponseDto> historicalTickerEarnings(@PathVariable String ticker) {
        log.info("GET /api/historicalEarnings/{} - fetching historical ticker earnings (authenticated)", ticker);

        try {
            ticker = ticker.toUpperCase();
            TickerEarningsResponseDto response = earningsService.getTickerEarnings(ticker);

            if (response != null && response.data() != null) {
                log.info("Returning {} historical earnings data points for {}", response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No historical earnings data available for {}", ticker);
                return ResponseEntity.ok(new TickerEarningsResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching historical earnings for ticker {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

