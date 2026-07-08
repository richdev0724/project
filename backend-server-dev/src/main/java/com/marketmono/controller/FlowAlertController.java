package com.marketmono.controller;

import com.marketmono.dto.FlowAlertPageResponse;
import com.marketmono.dto.FlowAlertResponseDto;
import com.marketmono.entity.FlowAlert;
import com.marketmono.repository.FlowAlertSpecifications.FlowAlertFilter;
import com.marketmono.service.FlowAlertMapperService;
import com.marketmono.service.FlowAlertService;
import com.marketmono.util.DateUtils;
import com.marketmono.util.Pagination;
import com.marketmono.util.RequestParamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for flow alerts endpoints
 * V1 API - uses upstream market data provider data source
 */
@RestController
@RequestMapping("/api/v1/flow")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class FlowAlertController {
    
    private final FlowAlertService flowAlertService;
    private final FlowAlertMapperService mapperService;
    
    /**
     * Get flow alerts with pagination and filtering
     * GET /api/flow?page=1&pageSize=100&tickers=AAPL,MSFT&ignoreTickers=SPX&minDte=1&maxDte=30...
     */
    @GetMapping
    public ResponseEntity<FlowAlertPageResponse> getFlowAlerts(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "100") int pageSize,
        @RequestParam(required = false) String tickers,
        @RequestParam(required = false) String ignoreTickers,
        @RequestParam(required = false) String marketCap,
        @RequestParam(required = false) Integer minDte,
        @RequestParam(required = false) Integer maxDte,
        @RequestParam(required = false) BigDecimal minimumPremium,
        @RequestParam(required = false) BigDecimal maximumPremium,
        @RequestParam(required = false) Integer minimumSize,
        @RequestParam(required = false) Integer maximumSize,
        @RequestParam(required = false) Integer minimumVolume,
        @RequestParam(required = false) Integer maximumVolume,
        @RequestParam(required = false) String optionType,
        @RequestParam(required = false) Boolean calls,
        @RequestParam(required = false) Boolean puts,
        @RequestParam(required = false) Boolean sweeps,
        @RequestParam(required = false) Boolean floor,
        @RequestParam(required = false) Boolean hasSweep,
        @RequestParam(required = false) Boolean hasFloor,
        @RequestParam(required = false) Boolean hasSingleleg,
        @RequestParam(required = false) Boolean hasMultileg,
        @RequestParam(required = false) Boolean allOpeningTrades
    ) {
        log.debug("Fetching flow alerts: page={}, pageSize={}, tickers={}, ignoreTickers={}, marketCap={}", 
            page, pageSize, tickers, ignoreTickers, marketCap);
        
        List<String> tickerList = RequestParamUtils.parseUpperCsvPreservingEmptyTokens(tickers);
        List<String> ignoreTickerList = RequestParamUtils.parseUpperCsvPreservingEmptyTokens(ignoreTickers);
        List<String> marketCapCategories = RequestParamUtils.parseLowerCsvPreservingEmptyTokens(marketCap);

        PageRequest pageable = Pagination.pageRequest(
            page,
            pageSize,
            Sort.by(Sort.Direction.DESC, "tradeStartTime")
        );
        
        // Fetch data with filters
        Page<FlowAlert> alertPage;
        if (tickerList != null || ignoreTickerList != null || marketCapCategories != null || 
            minDte != null || maxDte != null || minimumPremium != null || maximumPremium != null || 
            minimumSize != null || maximumSize != null || minimumVolume != null || maximumVolume != null || 
            optionType != null || calls != null || puts != null ||
            sweeps != null || floor != null || hasSweep != null || hasFloor != null || 
            hasSingleleg != null || hasMultileg != null || allOpeningTrades != null) {
            
            // Convert DTE to dates
            LocalDate now = DateUtils.getCurrentDateInEasternTime();
            LocalDate minExpiryDate = (minDte != null) ? now.plusDays(minDte) : null;
            LocalDate maxExpiryDate = (maxDte != null) ? now.plusDays(maxDte) : null;
            
            // Convert market cap categories to numeric ranges
            Long minMarketCap = null;
            Long maxMarketCap = null;
            if (marketCapCategories != null && !marketCapCategories.isEmpty()) {
                for (String category : marketCapCategories) {
                    if (category == null) continue;
                    
                    var marketCapRange = switch (category.toLowerCase()) {
                        case "micro" -> new long[]{0L, 300_000_000L};
                        case "small" -> new long[]{300_000_000L, 2_000_000_000L};
                        case "mid" -> new long[]{2_000_000_000L, 10_000_000_000L};
                        case "large" -> new long[]{10_000_000_000L, 200_000_000_000L};
                        case "mega" -> new long[]{200_000_000_000L, Long.MAX_VALUE};
                        default -> null;
                    };
                    
                    if (marketCapRange != null) {
                        minMarketCap = (minMarketCap == null) ? marketCapRange[0] : Math.min(minMarketCap, marketCapRange[0]);
                        maxMarketCap = (maxMarketCap == null) ? marketCapRange[1] : Math.max(maxMarketCap, marketCapRange[1]);
                    }
                }
            }
            
            var filter = new FlowAlertFilter(
                tickerList,
                ignoreTickerList,
                minExpiryDate,
                maxExpiryDate,
                null,
                minimumPremium,
                maximumPremium,
                minimumSize,
                maximumSize,
                minimumVolume,
                maximumVolume,
                minMarketCap,
                maxMarketCap,
                optionType,
                calls,
                puts,
                sweeps,
                floor,
                hasSweep,
                hasFloor,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                hasSingleleg,
                hasMultileg,
                allOpeningTrades,
                null,
                null,
                null,
                null,
                null
            );
            
            alertPage = flowAlertService.getFlowAlertsWithFilters(filter, pageable);
        } else {
            alertPage = flowAlertService.getFlowAlerts(pageable);
        }
        
        // Map to DTOs
        List<FlowAlertResponseDto> results = mapperService.toDtoList(alertPage.getContent());
        
        FlowAlertPageResponse response = new FlowAlertPageResponse(results, alertPage.getTotalElements());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Trigger manual sync of flow alerts
     * POST /api/flow/sync
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncFlowAlerts() {
        log.info("Manual flow alerts sync triggered");
        
        try {
            flowAlertService.syncFlowAlerts();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Flow alerts sync initiated successfully"
            ));
        } catch (Exception e) {
            log.error("Error initiating manual sync", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to initiate flow alerts sync: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get flow alert statistics
     * GET /api/flow/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = flowAlertService.getStatistics();
        return ResponseEntity.ok(stats);
    }
}
