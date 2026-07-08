package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.DashboardMarketPerformanceResponse;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.entity.DailyStockStateSnapshot;
import com.marketmono.entity.DashboardOhlcSnapshot;
import com.marketmono.repository.DailyStockStateSnapshotRepository;
import com.marketmono.repository.DashboardOhlcSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardMarketPerformanceServiceTest {

    private static final String SERIES_JSON = """
        [{"timestamp":1700000000000,"date":"2026-04-22","open":500.0,"high":505.0,"low":495.0,"close":502.0,"volume":1000}]
        """;

    @Mock
    private DashboardOhlcSnapshotRepository dashboardOhlcSnapshotRepository;

    @Mock
    private DailyStockStateSnapshotRepository dailyStockStateSnapshotRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private DashboardMarketPerformanceService dashboardMarketPerformanceService;

    @Test
    void shouldServeFromSnapshotsWhenAllTickersHaveFreshCoverage() {
        List<DashboardOhlcSnapshot> snapshots = List.of("SPY", "QQQ", "IWM", "DIA").stream()
            .map(ticker -> DashboardOhlcSnapshot.builder()
                .ticker(ticker)
                .timeframe("1D")
                .previousSessionClose(BigDecimal.valueOf(500.0))
                .series(SERIES_JSON)
                .fetchedAt(Instant.now())
                .build())
            .toList();

        when(dashboardOhlcSnapshotRepository.findByTimeframeAndTickerIn(eq("1D"), anyCollection()))
            .thenReturn(snapshots);

        DashboardMarketPerformanceResponse response = dashboardMarketPerformanceService.getMarketPerformance("1D");

        assertThat(response.tickers()).hasSize(4);
        assertThat(response.tickers().get("SPY").previousSessionClose())
            .isEqualByComparingTo(BigDecimal.valueOf(500.0));
        assertThat(response.tickers().get("SPY").series()).hasSize(1);
        verify(unusualWhalesClient, never()).getOhlcDataUncached(any(), any(), any(), any(), any(), any());
        verify(unusualWhalesClient, never()).getStockStateForStrataCollector(any());
    }

    @Test
    void shouldReturnEmptySeriesWithoutLiveFallbackWhenSnapshotsMissing() {
        when(dashboardOhlcSnapshotRepository.findByTimeframeAndTickerIn(eq("1D"), anyCollection()))
            .thenReturn(List.of());

        DashboardMarketPerformanceResponse response = dashboardMarketPerformanceService.getMarketPerformance("1D");

        assertThat(response.tickers()).hasSize(4);
        assertThat(response.tickers().get("SPY").series()).isEmpty();
        verify(unusualWhalesClient, never()).getOhlcDataUncached(any(), any(), any(), any(), any(), any());
        verify(unusualWhalesClient, never()).getStockStateForStrataCollector(any());
    }

    @Test
    void collectionUsesDailyStockStatePrevCloseAndWritesOneConsistentCycle() {
        // Empty store -> cold-start path runs regardless of market window.
        when(dashboardOhlcSnapshotRepository.existsByFetchedAtAfter(any())).thenReturn(false);
        when(dashboardOhlcSnapshotRepository.findByTimeframeAndTickerIn(anyString(), anyCollection()))
            .thenReturn(List.of());
        when(dashboardOhlcSnapshotRepository.deleteByFetchedAtBefore(any())).thenReturn(0);

        // Previous-session closes come from the daily stock-state store
        // (pulled once per trading day), not from a per-cycle upstream call.
        List<DailyStockStateSnapshot> dailyRows = List.of(
                "SPY", "QQQ", "IWM", "DIA", "NVDA", "META", "MSFT", "AAPL", "GOOG", "AMZN", "TSLA").stream()
            .map(ticker -> DailyStockStateSnapshot.builder()
                .ticker(ticker)
                .snapshotDate(LocalDate.now(ZoneId.of("America/New_York")))
                .close(new BigDecimal("156.00"))
                .prevClose(new BigDecimal("150.00"))
                .fetchedAt(Instant.now())
                .build())
            .toList();
        when(dailyStockStateSnapshotRepository.findLatestByTickerIn(anyCollection())).thenReturn(dailyRows);

        when(unusualWhalesClient.getOhlcDataUncached(anyString(), anyString(), any(), any(), any(), any()))
            .thenReturn(sampleOhlcResponse());

        dashboardMarketPerformanceService.collectAndStoreSnapshots();

        // No per-cycle stock-state calls anymore.
        verify(unusualWhalesClient, never()).getStockStateForStrataCollector(anyString());

        ArgumentCaptor<String> timeframeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BigDecimal> prevCloseCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<Instant> fetchedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(dashboardOhlcSnapshotRepository, atLeastOnce()).upsert(
            anyString(), timeframeCaptor.capture(), prevCloseCaptor.capture(), anyString(), fetchedAtCaptor.capture());

        // Every stored row carries the daily stock-state prev_close as the 1D baseline...
        assertThat(prevCloseCaptor.getAllValues())
            .allSatisfy(value -> assertThat(value).isEqualByComparingTo(new BigDecimal("150.00")));

        // ...and every ticker within a timeframe batch shares one fetchedAt so
        // the widget always reads a consistent snapshot across tickers.
        List<String> timeframes = timeframeCaptor.getAllValues();
        List<Instant> fetchedAts = fetchedAtCaptor.getAllValues();
        java.util.Map<String, java.util.Set<Instant>> fetchedAtByTimeframe = new java.util.HashMap<>();
        for (int i = 0; i < timeframes.size(); i++) {
            fetchedAtByTimeframe
                .computeIfAbsent(timeframes.get(i), key -> new HashSet<>())
                .add(fetchedAts.get(i));
        }
        assertThat(fetchedAtByTimeframe.values())
            .allSatisfy(instants -> assertThat(instants).hasSize(1));
    }

    /**
     * One regular-hours weekday candle so every timeframe filter keeps it
     * (intraday filters require weekday + 9:30-16:00 ET; daily filters
     * require market_time "r").
     */
    private UnusualWhalesOhlcDto sampleOhlcResponse() {
        LocalDate weekday = LocalDate.now(ZoneId.of("America/New_York"));
        while (weekday.getDayOfWeek() == DayOfWeek.SATURDAY || weekday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            weekday = weekday.minusDays(1);
        }
        String start = weekday.atTime(10, 0).atZone(ZoneId.of("America/New_York"))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String end = weekday.atTime(10, 1).atZone(ZoneId.of("America/New_York"))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new UnusualWhalesOhlcDto(List.of(new UnusualWhalesOhlcDto.OhlcItem(
            start, end, weekday.toString(),
            "500.0", "505.0", "495.0", "502.0", 1000L, 5000L, "r")));
    }
}
