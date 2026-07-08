package com.marketmono.controller;

import com.marketmono.dto.OptionContractHistoricChainDto;
import com.marketmono.service.OptionContractHistoricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for option contract historic data from upstream market data provider
 * Endpoint: GET /api/v1/option-contract-historic/{id}
 */
@RestController
@RequestMapping("/api/v1/option-contract-historic")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OptionContractHistoricController {
    
    private final OptionContractHistoricService optionContractHistoricService;
    
    /**
     * Get historic data for an option contract
     * Returns daily trading data for the given option contract
     * 
     * @param id Option contract in ISO format (e.g., TSLA230526P00167500)
     * @param limit Optional limit for number of results (min: 1)
     * @return Historic data for the option contract
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOptionContractHistoric(
            @PathVariable String id,
            @RequestParam(required = false) Integer limit
    ) {
        log.info("GET /api/v1/option-contract-historic/{} - limit: {}", id, limit);
        
        try {
            List<OptionContractHistoricChainDto> data = 
                optionContractHistoricService.getOptionContractHistoric(id, limit);
            
            log.info("Returning {} historic data points for option contract {}", data.size(), id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "chains", data
            ));
        } catch (Exception e) {
            log.error("Error fetching historic data for option contract {}", id, e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}



