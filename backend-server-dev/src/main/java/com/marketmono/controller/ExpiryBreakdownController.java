package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.ExpiryBreakdownResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/expiry-breakdown")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ExpiryBreakdownController {

    private final UnusualWhalesClient unusualWhalesClient;

    @GetMapping("/{ticker}")
    public ResponseEntity<ExpiryBreakdownResponseDto> getExpiryBreakdown(@PathVariable String ticker) {
        String normalizedTicker = ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
        log.info("GET /api/v1/expiry-breakdown/{}", normalizedTicker);
        return ResponseEntity.ok(unusualWhalesClient.getExpiryBreakdown(normalizedTicker));
    }
}
