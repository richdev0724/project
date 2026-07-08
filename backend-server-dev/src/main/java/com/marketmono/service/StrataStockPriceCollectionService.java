package com.marketmono.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.entity.StrataStockPrice;
import com.marketmono.repository.StrataStockPriceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class StrataStockPriceCollectionService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime POLL_WINDOW_OPEN = LocalTime.of(9, 30);
    private static final LocalTime POLL_WINDOW_CLOSE = LocalTime.of(16, 15);
    private static final Duration ROW_RETENTION = Duration.ofMinutes(5);

    private final UnusualWhalesClient unusualWhalesClient;
    private final StrataStockPriceTickerUniverseService tickerUniverseService;
    private final StrataStockPriceRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Autowired
    public StrataStockPriceCollectionService(
        UnusualWhalesClient unusualWhalesClient,
        StrataStockPriceTickerUniverseService tickerUniverseService,
        StrataStockPriceRepository repository,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this(unusualWhalesClient, tickerUniverseService, repository, objectMapper, Clock.systemUTC(), meterRegistry);
    }

    StrataStockPriceCollectionService(
        UnusualWhalesClient unusualWhalesClient,
        StrataStockPriceTickerUniverseService tickerUniverseService,
        StrataStockPriceRepository repository,
        ObjectMapper objectMapper,
        Clock clock,
        MeterRegistry meterRegistry
    ) {
        this.unusualWhalesClient = unusualWhalesClient;
        this.tickerUniverseService = tickerUniverseService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public StrataStockPriceCollectionResult collectLatestPrices() {
        ZonedDateTime marketNow = Instant.now(clock).atZone(MARKET_ZONE);
        if (!isWithinPollingWindow(marketNow)) {
            log.debug("[STRATA-STOCK-PRICE] Skipping collection outside market window at {}", marketNow);
            return new StrataStockPriceCollectionResult(List.of(), List.of(), 0);
        }

        Timer.Sample cycleTimer = Timer.start(meterRegistry);
        StrataStockPriceTickerUniverse universe = tickerUniverseService.getTickerUniverse();
        Map<String, List<StrataTickerMapping>> mappingsBySourceTicker = groupBySourceTicker(universe.mappings());
        List<StrataStockPrice> rows = new ArrayList<>();
        List<String> successfulSourceTickers = new ArrayList<>();
        List<String> failedSourceTickers = new ArrayList<>();

        for (Map.Entry<String, List<StrataTickerMapping>> entry : mappingsBySourceTicker.entrySet()) {
            String sourceTicker = entry.getKey();
            try {
                StockStateResponse response = unusualWhalesClient.getStockStateForStrataCollector(sourceTicker);
                if (response == null || response.data() == null) {
                    failedSourceTickers.add(sourceTicker);
                    recordSourceFailure(sourceTicker, "empty_response");
                    continue;
                }

                Instant fetchedAt = Instant.now(clock);
                String rawResponse = writeRawResponse(response);
                for (StrataTickerMapping mapping : entry.getValue()) {
                    rows.add(toRow(mapping, response.data(), rawResponse, fetchedAt));
                }
                successfulSourceTickers.add(sourceTicker);
                recordSourceSuccess(sourceTicker);
            } catch (RuntimeException ex) {
                failedSourceTickers.add(sourceTicker);
                recordSourceFailure(sourceTicker, "exception");
                log.warn("[STRATA-STOCK-PRICE] Failed to collect sourceTicker={}: {}",
                    sourceTicker, ex.getMessage());
            }
        }

        if (!rows.isEmpty()) {
            // Keep only the latest snapshot per ticker: replace prior rows for the tickers just fetched.
            Set<String> refreshedDisplayTickers = new LinkedHashSet<>();
            for (StrataStockPrice row : rows) {
                refreshedDisplayTickers.add(row.getDisplayTicker());
            }
            repository.deleteByDisplayTickerIn(refreshedDisplayTickers);
            repository.saveAll(rows);
        }

        Instant retentionCutoff = Instant.now(clock).minus(ROW_RETENTION);
        int purgedRows = repository.deleteByFetchedAtBefore(retentionCutoff);

        log.info(
            "[STRATA-STOCK-PRICE] Collection cycle finished: successfulSources={}, failedSources={}, rowsSaved={}, purgedRows={}",
            successfulSourceTickers.size(),
            failedSourceTickers.size(),
            rows.size(),
            purgedRows
        );
        String result = failedSourceTickers.isEmpty()
            ? "success"
            : successfulSourceTickers.isEmpty() ? "failure" : "partial";
        recordRowsSaved(rows.size(), result);
        cycleTimer.stop(Timer.builder("strata_stock_price.collection.duration")
            .tag("result", result)
            .register(meterRegistry));
        return new StrataStockPriceCollectionResult(successfulSourceTickers, failedSourceTickers, rows.size());
    }

    boolean isWithinPollingWindow(ZonedDateTime marketTime) {
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = marketTime.toLocalTime();
        return !time.isBefore(POLL_WINDOW_OPEN) && !time.isAfter(POLL_WINDOW_CLOSE);
    }

    private void recordSourceSuccess(String sourceTicker) {
        Counter.builder("strata_stock_price.collection.success")
            .tag("source_ticker", sourceTicker)
            .tag("result", "success")
            .register(meterRegistry)
            .increment();
    }

    private void recordSourceFailure(String sourceTicker, String reason) {
        Counter.builder("strata_stock_price.collection.failure")
            .tag("source_ticker", sourceTicker)
            .tag("result", "failure")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }

    private void recordRowsSaved(int rowsSaved, String result) {
        Counter.builder("strata_stock_price.collection.rows_saved")
            .tag("result", result)
            .register(meterRegistry)
            .increment(rowsSaved);
    }

    private Map<String, List<StrataTickerMapping>> groupBySourceTicker(List<StrataTickerMapping> mappings) {
        Map<String, List<StrataTickerMapping>> bySourceTicker = new LinkedHashMap<>();
        for (StrataTickerMapping mapping : mappings) {
            bySourceTicker.computeIfAbsent(mapping.sourceTicker(), ignored -> new ArrayList<>()).add(mapping);
        }
        return bySourceTicker;
    }

    private StrataStockPrice toRow(
        StrataTickerMapping mapping,
        StockStateResponse.StockStateData data,
        String rawResponse,
        Instant fetchedAt
    ) {
        BigDecimal closePrice = requiredDecimal(data.close(), "close", mapping.sourceTicker());
        BigDecimal prevClosePrice = requiredDecimal(data.prevClose(), "prev_close", mapping.sourceTicker());
        return StrataStockPrice.builder()
            .displayTicker(mapping.displayTicker())
            .sourceTicker(mapping.sourceTicker())
            .closePrice(closePrice)
            .prevClosePrice(prevClosePrice)
            .changePercent(changePercent(closePrice, prevClosePrice))
            .openPrice(optionalDecimal(data.open(), "open", mapping.sourceTicker()))
            .highPrice(optionalDecimal(data.high(), "high", mapping.sourceTicker()))
            .lowPrice(optionalDecimal(data.low(), "low", mapping.sourceTicker()))
            .volume(data.volume())
            .totalVolume(data.totalVolume())
            .marketTime(data.marketTime())
            .tapeTime(parseInstant(data.tapeTime(), mapping.sourceTicker()))
            .fetchedAt(fetchedAt)
            .rawResponse(rawResponse)
            .build();
    }

    private BigDecimal requiredDecimal(String value, String field, String sourceTicker) {
        BigDecimal parsed = optionalDecimal(value, field, sourceTicker);
        if (parsed == null) {
            throw new IllegalArgumentException("Missing required stock-state field " + field + " for " + sourceTicker);
        }
        return parsed;
    }

    private BigDecimal optionalDecimal(String value, String field, String sourceTicker) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                "Invalid stock-state field " + field + " for " + sourceTicker + ": " + value,
                ex
            );
        }
    }

    private BigDecimal changePercent(BigDecimal closePrice, BigDecimal prevClosePrice) {
        if (prevClosePrice.signum() <= 0) {
            throw new IllegalArgumentException("prev_close must be greater than zero");
        }
        return closePrice.subtract(prevClosePrice)
            .multiply(BigDecimal.valueOf(100))
            .divide(prevClosePrice, 6, RoundingMode.HALF_UP);
    }

    private Instant parseInstant(String value, String sourceTicker) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid tape_time for " + sourceTicker + ": " + value, ex);
        }
    }

    private String writeRawResponse(StockStateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize stock-state response", ex);
        }
    }
}
