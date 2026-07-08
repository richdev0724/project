package com.marketmono.controller;

import com.marketmono.dto.TickerSearchResultDto;
import com.marketmono.service.TickerSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class TickerSearchController {

    private final TickerSearchService tickerSearchService;

    @GetMapping("/ticker-search")
    public ResponseEntity<List<TickerSearchResultDto>> searchTickers(@RequestParam String query) {
        log.info("GET /api/v1/ticker-search?query={}", query);
        return ResponseEntity.ok(tickerSearchService.searchTickers(query));
    }

    @GetMapping("/ticker-search/company")
    public ResponseEntity<List<TickerSearchResultDto>> searchTickersByCompany(@RequestParam String query) {
        log.info("GET /api/v1/ticker-search/company?query={}", query);
        return ResponseEntity.ok(tickerSearchService.searchTickersByCompany(query));
    }
}
