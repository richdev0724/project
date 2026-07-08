package com.marketmono.controller;

import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.service.SpotExposureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for GEX Strike Expiry Range data from upstream market data provider
 * Proxies: GET /api/stock/{ticker}/spot-exposures/expiry-strike
 * Endpoint: GET /api/v1/GEX-Strike-Expiry-Range/{ticker}
 */
@RestController
@RequestMapping("/api/v1/GEX-Strike-Expiry-Range")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class GexStrikeExpiryRangeController {

    private final SpotExposureService spotExposureService;

    /**
     * Get spot GEX exposure data aggregated by expiry and strike for a ticker.
     *
     * @param ticker      Stock ticker symbol
     * @param expirations Array of expiration dates (required)
     * @param date        Trading date in YYYY-MM-DD format (optional, defaults to last trading date)
     * @param limit       Number of results (default: 500, max: 500, min: 1)
     * @param page        Page number for pagination (starts at 0)
     * @param minStrike   Minimum strike price filter (optional)
     * @param maxStrike   Maximum strike price filter (optional)
     * @param minDte      Minimum days to expiry filter (optional)
     * @param maxDte      Maximum days to expiry filter (optional)
     * @return Spot GEX exposure data grouped by expiry and strike
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getGexStrikeExpiryRange(
            @PathVariable String ticker,
            @RequestParam(required = true) List<String> expirations,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "min_strike", required = false) Integer minStrike,
            @RequestParam(name = "max_strike", required = false) Integer maxStrike,
            @RequestParam(name = "min_dte", required = false) Integer minDte,
            @RequestParam(name = "max_dte", required = false) Integer maxDte
    ) {
        log.info("GET /api/v1/GEX-Strike-Expiry-Range/{} - expirations: {} (total: {}), date: {}, limit: {}, page: {}",
                ticker, expirations.size() > 5 ? expirations.subList(0, 5) + "..." : expirations,
                expirations.size(), date, limit, page);

        try {
            List<SpotExposureByStrikeDto> data =
                    spotExposureService.getSpotExposureByExpiryStrike(
                            ticker, expirations, date, limit, page,
                            minStrike, maxStrike, minDte, maxDte
                    );

            log.info("Returning {} GEX strike-expiry data points for {}", data.size(), ticker);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", data
            ));
        } catch (Exception e) {
            log.error("Error fetching GEX strike-expiry range for {}", ticker, e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
