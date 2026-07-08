package com.marketmono.controller;

import com.marketmono.dto.OptionContractItemDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.OptionContractsAllService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for all option contracts for a ticker from upstream market data provider
 * Endpoint: GET /api/v1/option-contracts-all/{ticker}
 */
@RestController
@RequestMapping("/api/v1/option-contracts-all")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OptionContractsAllController {
    
    private final OptionContractsAllService optionContractsAllService;
    
    /**
     * Get all option contracts for a ticker
     * Returns all option contracts for the given ticker with various filter options
     * 
     * @param ticker Stock ticker symbol
     * @param expiry Single expiry date in ISO date format (YYYY-MM-DD)
     * @param optionType Option type to filter by ("call", "Call", "put", "Put")
     * @param volGreaterOi Only return chains where volume > open interest
     * @param excludeZeroVolChains Only return chains where volume > 0
     * @param excludeZeroDte Only return chains which do not expire on the same day
     * @param excludeZeroOiChains Only return chains where open interest > 0
     * @param maybeOtmOnly Only return chains which are out of the money
     * @param optionSymbol Options symbols to filter by (can specify multiple)
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @param page Page number for pagination (starts on page 0)
     * @return All option contracts for the ticker matching filters
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getOptionContractsAll(
            @PathVariable String ticker,
            @RequestParam(required = false) String expiry,
            @RequestParam(name = "option_type", required = false) String optionType,
            @RequestParam(name = "vol_greater_oi", required = false) Boolean volGreaterOi,
            @RequestParam(name = "exclude_zero_vol_chains", required = false) Boolean excludeZeroVolChains,
            @RequestParam(name = "exclude_zero_dte", required = false) Boolean excludeZeroDte,
            @RequestParam(name = "exclude_zero_oi_chains", required = false) Boolean excludeZeroOiChains,
            @RequestParam(name = "maybe_otm_only", required = false) Boolean maybeOtmOnly,
            @RequestParam(name = "option_symbol", required = false) List<String> optionSymbol,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer page
    ) {
        log.info("GET /api/v1/option-contracts-all/{} - expiry: {}, optionType: {}, limit: {}", 
                 ticker, expiry, optionType, limit);
        
        try {
            List<OptionContractItemDto> data = optionContractsAllService.getOptionContractsAll(
                ticker, expiry, optionType, volGreaterOi, excludeZeroVolChains, 
                excludeZeroDte, excludeZeroOiChains, maybeOtmOnly, optionSymbol, limit, page
            );
            
            log.info("Returning {} option contracts for ticker {}", data.size(), ticker);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching option contracts for ticker {}", ticker, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}

