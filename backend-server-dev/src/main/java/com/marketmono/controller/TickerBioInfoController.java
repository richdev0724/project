package com.marketmono.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.dto.TickerBioInfoDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ticker-bio-info")
@CrossOrigin(origins = "*")
@Slf4j
public class TickerBioInfoController {

    private Map<String, TickerBioInfoDto> tickerBioMap;

    @PostConstruct
    public void init() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<TickerBioInfoDto> list = mapper.readValue(
            new ClassPathResource("ticker_bio_info.json").getInputStream(),
            new TypeReference<>() {}
        );
        tickerBioMap = list.stream()
            .collect(Collectors.toMap(dto -> dto.ticker().toUpperCase(), Function.identity()));
        log.info("Loaded {} ticker bio entries", tickerBioMap.size());
    }

    @GetMapping
    public ResponseEntity<TickerBioInfoDto> getTickerBioInfo(@RequestParam String ticker) {
        log.info("GET /api/v1/ticker-bio-info?ticker={}", ticker);
        TickerBioInfoDto result = tickerBioMap.get(ticker.toUpperCase());
        if (result == null) {
            log.warn("Ticker not found in bio info: {}", ticker);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
