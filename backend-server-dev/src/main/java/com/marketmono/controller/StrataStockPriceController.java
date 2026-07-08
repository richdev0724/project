package com.marketmono.controller;

import com.marketmono.dto.StrataStockPriceLatestResponse;
import com.marketmono.service.StrataStockPriceLatestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/strata/stock-prices")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StrataStockPriceController {

    private final StrataStockPriceLatestService latestService;

    @GetMapping("/latest")
    public ResponseEntity<StrataStockPriceLatestResponse> getLatest(@RequestParam String tickers) {
        List<String> requestedTickers = Arrays.stream(tickers.split(","))
            .map(String::trim)
            .filter(ticker -> !ticker.isBlank())
            .toList();
        return ResponseEntity.ok(latestService.getLatest(requestedTickers));
    }
}
