package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionContractsResponse;
import com.marketmono.service.UnusualWhalesQueryParamMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/screener")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ScreenerController {
    
    private final UnusualWhalesClient unusualWhalesClient;
    private final UnusualWhalesQueryParamMapper unusualWhalesQueryParamMapper;

    /**
     * Screen option contracts with advanced filtering
     * Proxies the upstream market data provider screener API
     * 
     * @return OptionContractsResponse containing filtered option contracts
     */
    @GetMapping("/option-contracts")
    public ResponseEntity<OptionContractsResponse> getOptionContracts(
            @RequestParam MultiValueMap<String, String> requestParams
    ) {
        MultiValueMap<String, String> normalizedParams = normalizeTickerSymbol(requestParams);

        log.info("Fetching option contracts with filters: ticker={}, is_otm={}, min_premium={}, limit={}", 
                 normalizedParams.getFirst("ticker_symbol"),
                 normalizedParams.getFirst("is_otm"),
                 normalizedParams.getFirst("min_premium"),
                 normalizedParams.getFirst("limit"));

        OptionContractsResponse response = unusualWhalesClient.getOptionContracts(
            unusualWhalesQueryParamMapper.toParams(normalizedParams)
        );
        return ResponseEntity.ok(response);
    }

    private MultiValueMap<String, String> normalizeTickerSymbol(MultiValueMap<String, String> requestParams) {
        MultiValueMap<String, String> normalized = new LinkedMultiValueMap<>(requestParams);
        if (!normalized.containsKey("ticker_symbol")) {
            return normalized;
        }

        normalized.computeIfPresent("ticker_symbol", (ignoredKey, values) -> values == null
            ? null
            : values.stream()
                .map(this::normalizeTickerValue)
                .toList());
        return normalized;
    }

    private String normalizeTickerValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .map(part -> part.toUpperCase(Locale.ROOT))
            .collect(Collectors.joining(","));
    }
}

