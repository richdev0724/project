package com.marketmono.service;

import com.marketmono.config.StrataStockPriceCollectionProperties;
import com.marketmono.dto.StrataStockPriceLatestDto;
import com.marketmono.dto.StrataStockPriceLatestResponse;
import com.marketmono.dto.StrataStockPriceMissingDto;
import com.marketmono.entity.StrataStockPrice;
import com.marketmono.repository.StrataStockPriceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StrataStockPriceLatestService {

    private final StrataStockPriceRepository repository;
    private final Clock clock;
    private final int staleAfterSeconds;
    private final MeterRegistry meterRegistry;

    @Autowired
    public StrataStockPriceLatestService(
        StrataStockPriceRepository repository,
        StrataStockPriceCollectionProperties properties,
        MeterRegistry meterRegistry
    ) {
        this(repository, Clock.systemUTC(), properties.getStaleAfterSeconds(), meterRegistry);
    }

    StrataStockPriceLatestService(
        StrataStockPriceRepository repository,
        Clock clock,
        int staleAfterSeconds,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.clock = clock;
        this.staleAfterSeconds = staleAfterSeconds;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public StrataStockPriceLatestResponse getLatest(List<String> requestedTickers) {
        List<StrataTickerMapping> mappings = normalizeRequestedTickers(requestedTickers);
        List<String> displayTickers = mappings.stream()
            .map(StrataTickerMapping::displayTicker)
            .distinct()
            .toList();
        if (displayTickers.isEmpty()) {
            return new StrataStockPriceLatestResponse(List.of(), List.of());
        }

        Map<String, StrataStockPrice> rowsByDisplayTicker = new LinkedHashMap<>();
        for (StrataStockPrice row : repository.findLatestByDisplayTickers(displayTickers)) {
            rowsByDisplayTicker.put(row.getDisplayTicker(), row);
        }

        List<StrataStockPriceLatestDto> prices = new ArrayList<>();
        List<StrataStockPriceMissingDto> missing = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (StrataTickerMapping mapping : mappings) {
            StrataStockPrice row = rowsByDisplayTicker.get(mapping.displayTicker());
            if (row == null) {
                missing.add(new StrataStockPriceMissingDto(
                    mapping.displayTicker(),
                    mapping.sourceTicker(),
                    "not_persisted"
                ));
                continue;
            }
            StrataStockPriceLatestDto latestPrice = toDto(row, now);
            if (latestPrice.stale()) {
                recordStaleLatest(latestPrice);
            }
            prices.add(latestPrice);
        }
        return new StrataStockPriceLatestResponse(prices, missing);
    }

    private void recordStaleLatest(StrataStockPriceLatestDto latestPrice) {
        Counter.builder("strata_stock_price.latest.stale")
            .tag("source_ticker", latestPrice.sourceTicker())
            .tag("result", "stale")
            .register(meterRegistry)
            .increment();
    }

    private List<StrataTickerMapping> normalizeRequestedTickers(List<String> requestedTickers) {
        if (requestedTickers == null || requestedTickers.isEmpty()) {
            return List.of();
        }

        Map<String, StrataTickerMapping> mappings = new LinkedHashMap<>();
        for (String ticker : requestedTickers) {
            try {
                StrataTickerMapping mapping = StrataTickerPolicy.resolve(ticker);
                mappings.putIfAbsent(mapping.displayTicker(), mapping);
            } catch (IllegalArgumentException ignored) {
                // Invalid display tickers are omitted from the persisted lookup.
            }
        }
        return new ArrayList<>(mappings.values());
    }

    private StrataStockPriceLatestDto toDto(StrataStockPrice row, Instant now) {
        long ageSeconds = Math.max(0, Duration.between(row.getFetchedAt(), now).toSeconds());
        return new StrataStockPriceLatestDto(
            row.getDisplayTicker(),
            row.getSourceTicker(),
            row.getClosePrice(),
            row.getPrevClosePrice(),
            row.getChangePercent(),
            row.getOpenPrice(),
            row.getHighPrice(),
            row.getLowPrice(),
            row.getVolume(),
            row.getTotalVolume(),
            row.getMarketTime(),
            row.getTapeTime(),
            row.getFetchedAt(),
            ageSeconds > staleAfterSeconds,
            ageSeconds
        );
    }
}
