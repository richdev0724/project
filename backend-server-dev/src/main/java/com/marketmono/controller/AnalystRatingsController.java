package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.AnalystRatingsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for analyst ratings endpoint
 */
@RestController
@RequestMapping("/api/v1/analyst-ratings")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AnalystRatingsController {

    private final UnusualWhalesClient unusualWhalesClient;

    /**
     * Get analyst ratings for a specific ticker
     * Endpoint: GET /api/v1/analyst-ratings/{ticker}
     * 
     * @param ticker Stock ticker symbol
     * @param limit Optional limit for number of results (default: 500, max: 500, min: 1)
     * @return Analyst ratings response containing rating data
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<AnalystRatingsResponseDto> getAnalystRatings(
            @PathVariable String ticker,
            @RequestParam(required = false) Integer limit) {
        
        log.info("GET /api/v1/analyst-ratings/{} - limit: {}", ticker, limit);
        
        AnalystRatingsResponseDto response = unusualWhalesClient.getAnalystRatings(ticker, limit);
        
        if (response == null || response.data() == null) {
            log.warn("No analyst ratings data available for ticker: {}", ticker);
            // Return empty array instead of 404 to prevent frontend crashes
            return ResponseEntity.ok(new AnalystRatingsResponseDto(java.util.Collections.emptyList()));
        }
        
        log.info("Returning {} analyst ratings for ticker: {}", response.data().size(), ticker);
        return ResponseEntity.ok(response);
    }
}




