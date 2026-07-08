package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionChainsResponseDto;
import com.marketmono.service.StrataTickerFamilyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for option chains endpoint
 * Returns all option symbols for a given ticker
 */
@RestController
@RequestMapping("/api/v1/option-chains")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OptionChainsController {

    private final UnusualWhalesClient unusualWhalesClient;
    private final StrataTickerFamilyResolver strataTickerFamilyResolver;

    /**
     * Get all option symbols for a ticker
     * Endpoint: GET /api/v1/option-chains/{ticker}
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional market date in YYYY-MM-DD format (defaults to current/last market day)
     * @return Option chains response containing list of option symbols
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<OptionChainsResponseDto> getOptionChains(
            @PathVariable String ticker,
            @RequestParam(required = false) String date) {
        
        log.info("GET /api/v1/option-chains/{} - date: {}", ticker, date);
        
        List<String> roots = strataTickerFamilyResolver.optionChainRoots(ticker);
        Set<String> mergedChains = new LinkedHashSet<>();
        RuntimeException lastFailure = null;

        for (String root : roots) {
            try {
                OptionChainsResponseDto response = unusualWhalesClient.getOptionChains(root, date);
                if (response != null && response.data() != null) {
                    mergedChains.addAll(response.data());
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("Option chains root {} failed for display ticker {}: {}", root, ticker, exception.getMessage());
            }
        }

        if (mergedChains.isEmpty() && lastFailure != null) {
            throw lastFailure;
        }

        if (mergedChains.isEmpty()) {
            log.warn("No option chains data available for ticker: {}", ticker);
            // Return empty array instead of 404 to prevent frontend crashes
            return ResponseEntity.ok(new OptionChainsResponseDto(java.util.Collections.emptyList()));
        }

        log.info("Returning {} option symbols for ticker: {}", mergedChains.size(), ticker);
        return ResponseEntity.ok(new OptionChainsResponseDto(List.copyOf(mergedChains)));
    }
}



