package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.entity.StrataStockPrice;
import com.marketmono.repository.StrataStockPriceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrataStockPriceCollectionServiceTest {

    // Wednesday 2026-07-01 10:30 America/New_York, inside the Mon-Fri 9:30-16:15 polling window
    private static final Instant NOW = Instant.parse("2026-07-01T14:30:00Z");

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private StrataStockPriceTickerUniverseService tickerUniverseService;

    @Mock
    private StrataStockPriceRepository repository;

    private SimpleMeterRegistry meterRegistry;

    private StrataStockPriceCollectionService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new StrataStockPriceCollectionService(
            unusualWhalesClient,
            tickerUniverseService,
            repository,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            meterRegistry
        );
    }

    @Test
    void shouldCollectEachUniqueSourceTickerThroughUncachedClientAndPersistDisplayRows() {
        when(tickerUniverseService.getTickerUniverse()).thenReturn(new StrataStockPriceTickerUniverse(
            List.of(
                new StrataTickerMapping("SPX", "SPX"),
                new StrataTickerMapping("SPXW", "SPX"),
                new StrataTickerMapping("SPY", "SPY")
            ),
            new StrataTickerUniverseProvenance(1, 1, 1, 0)
        ));
        when(unusualWhalesClient.getStockStateForStrataCollector("SPX")).thenReturn(stockState("7499.36", "7440.43"));
        when(unusualWhalesClient.getStockStateForStrataCollector("SPY")).thenReturn(stockState("620.10", "618.00"));

        StrataStockPriceCollectionResult result = service.collectLatestPrices();

        assertThat(result.successfulSourceTickers()).containsExactly("SPX", "SPY");
        assertThat(result.failedSourceTickers()).isEmpty();
        verify(unusualWhalesClient).getStockStateForStrataCollector("SPX");
        verify(unusualWhalesClient).getStockStateForStrataCollector("SPY");
        verify(unusualWhalesClient, never()).getStockState("SPX");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StrataStockPrice>> rowsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).hasSize(3);
        assertThat(rowsCaptor.getValue())
            .filteredOn(row -> row.getDisplayTicker().equals("SPXW"))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.getSourceTicker()).isEqualTo("SPX");
                assertThat(row.getClosePrice()).isEqualByComparingTo("7499.36");
                assertThat(row.getPrevClosePrice()).isEqualByComparingTo("7440.43");
                assertThat(row.getChangePercent()).isEqualByComparingTo("0.792024");
                assertThat(row.getFetchedAt()).isEqualTo(NOW);
                assertThat(row.getRawResponse()).contains("\"close\":\"7499.36\"");
            });
        assertThat(meterRegistry.counter(
            "strata_stock_price.collection.success",
            "source_ticker", "SPX",
            "result", "success"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
            "strata_stock_price.collection.rows_saved",
            "result", "success"
        ).count()).isEqualTo(3.0);
        assertThat(meterRegistry.find("strata_stock_price.collection.duration").timer()).isNotNull();
    }

    @Test
    void shouldContinueCollectingWhenOneSourceTickerFails() {
        when(tickerUniverseService.getTickerUniverse()).thenReturn(new StrataStockPriceTickerUniverse(
            List.of(
                new StrataTickerMapping("SPX", "SPX"),
                new StrataTickerMapping("SPY", "SPY")
            ),
            new StrataTickerUniverseProvenance(2, 0, 0, 0)
        ));
        when(unusualWhalesClient.getStockStateForStrataCollector("SPX")).thenThrow(new IllegalStateException("upstream"));
        when(unusualWhalesClient.getStockStateForStrataCollector("SPY")).thenReturn(stockState("620.10", "618.00"));

        StrataStockPriceCollectionResult result = service.collectLatestPrices();

        assertThat(result.successfulSourceTickers()).containsExactly("SPY");
        assertThat(result.failedSourceTickers()).containsExactly("SPX");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StrataStockPrice>> rowsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).extracting(StrataStockPrice::getDisplayTicker).containsExactly("SPY");
        assertThat(meterRegistry.counter(
            "strata_stock_price.collection.failure",
            "source_ticker", "SPX",
            "result", "failure",
            "reason", "exception"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(
            "strata_stock_price.collection.rows_saved",
            "result", "partial"
        ).count()).isEqualTo(1.0);
    }

    @Test
    void shouldPersistFreshRowsAcrossRepeatedCollectionCycles() {
        when(tickerUniverseService.getTickerUniverse()).thenReturn(new StrataStockPriceTickerUniverse(
            List.of(new StrataTickerMapping("SPXW", "SPX")),
            new StrataTickerUniverseProvenance(1, 0, 0, 0)
        ));
        when(unusualWhalesClient.getStockStateForStrataCollector("SPX"))
            .thenReturn(stockState("7499.36", "7440.43"))
            .thenReturn(stockState("7504.60", "7440.43"));

        service.collectLatestPrices();
        service.collectLatestPrices();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StrataStockPrice>> rowsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(repository, org.mockito.Mockito.times(2)).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getAllValues().get(0))
            .singleElement()
            .satisfies(row -> assertThat(row.getClosePrice()).isEqualByComparingTo("7499.36"));
        assertThat(rowsCaptor.getAllValues().get(1))
            .singleElement()
            .satisfies(row -> assertThat(row.getClosePrice()).isEqualByComparingTo("7504.60"));
        verify(unusualWhalesClient, org.mockito.Mockito.times(2)).getStockStateForStrataCollector("SPX");
    }

    @Test
    void shouldReplacePriorRowsForRefreshedTickersAndPurgeStaleRows() {
        when(tickerUniverseService.getTickerUniverse()).thenReturn(new StrataStockPriceTickerUniverse(
            List.of(
                new StrataTickerMapping("SPX", "SPX"),
                new StrataTickerMapping("SPXW", "SPX")
            ),
            new StrataTickerUniverseProvenance(2, 0, 0, 0)
        ));
        when(unusualWhalesClient.getStockStateForStrataCollector("SPX")).thenReturn(stockState("7499.36", "7440.43"));

        service.collectLatestPrices();

        var order = inOrder(repository);
        order.verify(repository).deleteByDisplayTickerIn(Set.of("SPX", "SPXW"));
        order.verify(repository).saveAll(anyList());
        order.verify(repository).deleteByFetchedAtBefore(NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    void shouldPurgeStaleRowsEvenWhenNothingWasCollected() {
        when(tickerUniverseService.getTickerUniverse()).thenReturn(new StrataStockPriceTickerUniverse(
            List.of(new StrataTickerMapping("SPX", "SPX")),
            new StrataTickerUniverseProvenance(1, 0, 0, 0)
        ));
        when(unusualWhalesClient.getStockStateForStrataCollector("SPX")).thenThrow(new IllegalStateException("upstream"));

        service.collectLatestPrices();

        verify(repository, never()).saveAll(anyList());
        verify(repository).deleteByFetchedAtBefore(NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    void shouldSkipCollectionOutsideMarketWindow() {
        // Wednesday 2026-07-01 08:00 America/New_York, before the 9:30 open
        Instant beforeOpen = Instant.parse("2026-07-01T12:00:00Z");
        StrataStockPriceCollectionService outsideWindowService = new StrataStockPriceCollectionService(
            unusualWhalesClient,
            tickerUniverseService,
            repository,
            new ObjectMapper(),
            Clock.fixed(beforeOpen, ZoneOffset.UTC),
            meterRegistry
        );

        StrataStockPriceCollectionResult result = outsideWindowService.collectLatestPrices();

        assertThat(result.successfulSourceTickers()).isEmpty();
        assertThat(result.failedSourceTickers()).isEmpty();
        assertThat(result.rowsSaved()).isZero();
        verifyNoInteractions(unusualWhalesClient, tickerUniverseService, repository);
    }

    @Test
    void shouldOnlyPollMondayToFridayBetweenNineThirtyAndFourFifteenNewYork() {
        ZoneId newYork = ZoneId.of("America/New_York");

        assertThat(service.isWithinPollingWindow(ZonedDateTime.of(2026, 7, 1, 9, 29, 59, 0, newYork))).isFalse();
        assertThat(service.isWithinPollingWindow(ZonedDateTime.of(2026, 7, 1, 9, 30, 0, 0, newYork))).isTrue();
        assertThat(service.isWithinPollingWindow(ZonedDateTime.of(2026, 7, 1, 16, 15, 0, 0, newYork))).isTrue();
        assertThat(service.isWithinPollingWindow(ZonedDateTime.of(2026, 7, 1, 16, 16, 0, 0, newYork))).isFalse();
        // Saturday and Sunday inside the time-of-day window
        assertThat(service.isWithinPollingWindow(ZonedDateTime.of(2026, 7, 4, 12, 0, 0, 0, newYork))).isFalse();
        assertThat(service.isWithinPollingWindow(ZonedDateTime.of(2026, 7, 5, 12, 0, 0, 0, newYork))).isFalse();
    }

    private static StockStateResponse stockState(String close, String prevClose) {
        return new StockStateResponse(new StockStateResponse.StockStateData(
            close,
            prevClose,
            "2026-07-01T09:29:58Z",
            10_000L,
            12_000L,
            new BigDecimal(close).subtract(new BigDecimal("1.00")).toPlainString(),
            new BigDecimal(close).add(new BigDecimal("1.00")).toPlainString(),
            new BigDecimal(close).subtract(new BigDecimal("2.00")).toPlainString(),
            "market"
        ));
    }
}
