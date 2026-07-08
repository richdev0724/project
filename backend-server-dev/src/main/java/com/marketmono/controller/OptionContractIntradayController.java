package com.marketmono.controller;

import com.marketmono.dto.OptionContractIntradayDataDto;
import com.marketmono.service.OptionContractIntradayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for option contract intraday data from upstream market data provider
 * Endpoint: GET /api/v1/option-contract-intraday/{id}
 */
@RestController
@RequestMapping("/api/v1/option-contract-intraday")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OptionContractIntradayController {
    
    private final OptionContractIntradayService optionContractIntradayService;
    
    /**
     * Get intraday data for an option contract
     * Returns 1-minute interval data for the given option contract
     * 
     * @param id Option contract in ISO format (e.g., GOOGL260220P00310000)
     * @param date Optional date in YYYY-MM-DD format (defaults to current/last market day)
     * @return Intraday data for the option contract
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOptionContractIntraday(
            @PathVariable String id,
            @RequestParam(required = false) String date
    ) {
        log.info("GET /api/v1/option-contract-intraday/{} - date: {}", id, date);
        
        try {
            List<OptionContractIntradayDataDto> data = 
                optionContractIntradayService.getOptionContractIntraday(id, date);
            
            log.info("Returning {} intraday data points for option contract {}", data.size(), id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            log.error("Error fetching intraday data for option contract {}", id, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}



