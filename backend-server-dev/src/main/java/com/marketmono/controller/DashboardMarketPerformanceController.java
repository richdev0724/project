package com.marketmono.controller;

import com.marketmono.dto.DashboardMarketPerformanceResponse;
import com.marketmono.service.DashboardMarketPerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Dashboard endpoint for the batched market performance widget payload.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class DashboardMarketPerformanceController {

    private final DashboardMarketPerformanceService dashboardMarketPerformanceService;

    @GetMapping("/market-performance")
    public ResponseEntity<DashboardMarketPerformanceResponse> getMarketPerformance(
        @RequestParam String timeframe,
        @RequestParam(required = false) String tickers
    ) {
        log.info("GET /api/v1/dashboard/market-performance timeframe={} tickers={}", timeframe, tickers);

        try {
            List<String> requestedTickers = tickers == null || tickers.isBlank()
                ? null
                : Arrays.asList(tickers.split(","));
            DashboardMarketPerformanceResponse response =
                dashboardMarketPerformanceService.getMarketPerformance(timeframe, requestedTickers);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid market performance request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to build market performance response for timeframe {}", timeframe, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
