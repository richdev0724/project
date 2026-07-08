package com.marketmono.controller;

import com.marketmono.dto.GreekExposureByStrikeDto;
import com.marketmono.dto.GreekExposureResponseDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.GammaExposureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Greek exposure by strike endpoint
 * Provides greek exposure data aggregated by strike price
 * Endpoint: GET /api/v1/greek-exposure-by-strike/{ticker}
 */
@RestController
@RequestMapping("/api/v1/greek-exposure-by-strike")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class GreekExposureByStrikeController {
    
    private final GammaExposureService gammaExposureService;
    
    /**
     * Get greek exposure data aggregated by strike price
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date in YYYY-MM-DD format (optional, defaults to most recent trading day)
     * @return Greek exposure data aggregated by strike
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<GreekExposureResponseDto<GreekExposureByStrikeDto>> getGreekExposureByStrike(
            @PathVariable String ticker,
            @RequestParam(required = false) String date
    ) {
        log.info("GET /api/v1/greek-exposure-by-strike/{} - date: {}", ticker, date);
        
        try {
            var data = gammaExposureService.getGammaExposureByStrike(ticker.toUpperCase(), date);
            GreekExposureResponseDto<GreekExposureByStrikeDto> response =
                new GreekExposureResponseDto<>(data);
            
            if (response != null && response.data() != null) {
                log.info("Returning {} greek exposure by strike data points for {}", 
                    response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No greek exposure by strike data available for {}", ticker);
                return ResponseEntity.ok(new GreekExposureResponseDto<>(java.util.List.of()));
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching greek exposure by strike for {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
