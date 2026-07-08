package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.SectorPerformanceLiveResponse;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.entity.SectorPerformanceSnapshot;
import com.marketmono.repository.SectorPerformanceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectorPerformanceSnapshotServiceTest {

    private static final List<String> SECTOR_ETFS = List.of(
        "XLP", "XLU", "XLV", "XLB", "XLC", "XLY", "XLE", "XLF", "XLI", "XLK", "XLRE"
    );

    @Mock
    private SectorPerformanceSnapshotRepository sectorPerformanceSnapshotRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private SectorPerformanceSnapshotService sectorPerformanceSnapshotService;

    @Test
    void shouldServeFreshSnapshotsFromDatabaseWithoutUpstreamCalls() {
        SectorPerformanceSnapshot snapshot = SectorPerformanceSnapshot.builder()
            .ticker("XLK")
            .lastPrice(BigDecimal.valueOf(250.50))
            .prevClose(BigDecimal.valueOf(248.00))
            .dailyPct(1.01)
            .pct30d(4.2)
            .pct6m(11.7)
            .baseline30dClose(BigDecimal.valueOf(240.30))
            .baseline6mClose(BigDecimal.valueOf(224.26))
            .volume(5_000_000L)
            .lastDate(LocalDate.of(2026, 7, 2))
            .fetchedAt(Instant.now())
            .build();

        when(sectorPerformanceSnapshotRepository.findByTickerIn(anyCollection()))
            .thenReturn(List.of(snapshot));

        List<SectorPerformanceLiveResponse> results =
            sectorPerformanceSnapshotService.getSectorPerformanceFromDatabase();

        assertThat(results).hasSize(11);
        SectorPerformanceLiveResponse xlk = results.stream()
            .filter(r -> r.ticker().equals("XLK"))
            .findFirst()
            .orElseThrow();
        assertThat(xlk.lastClose()).isEqualTo(250.50);
        assertThat(xlk.prevClose()).isEqualTo(248.00);
        assertThat(xlk.dailyPct()).isEqualTo(1.01);
        assertThat(xlk.dailyChange()).isCloseTo(2.50, within(1e-6));
        assertThat(xlk.pct30d()).isEqualTo(4.2);
        assertThat(xlk.change30d()).isCloseTo(10.20, within(1e-6));
        assertThat(xlk.pct6m()).isEqualTo(11.7);
        assertThat(xlk.change6m()).isCloseTo(26.24, within(1e-6));
        assertThat(xlk.volume()).isEqualTo(5_000_000L);
        assertThat(xlk.lastDate()).isEqualTo("2026-07-02");
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldReturnNullMetricsWithoutUpstreamFallbackWhenSnapshotsMissing() {
        when(sectorPerformanceSnapshotRepository.findByTickerIn(anyCollection()))
            .thenReturn(List.of());

        List<SectorPerformanceLiveResponse> results =
            sectorPerformanceSnapshotService.getSectorPerformanceFromDatabase();

        assertThat(results).hasSize(11);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.lastClose()).isNull();
            assertThat(result.dailyPct()).isNull();
            assertThat(result.pct30d()).isNull();
            assertThat(result.pct6m()).isNull();
        });
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldTreatStaleSnapshotsAsMissing() {
        SectorPerformanceSnapshot staleSnapshot = SectorPerformanceSnapshot.builder()
            .ticker("XLE")
            .lastPrice(BigDecimal.valueOf(90.00))
            .dailyPct(0.5)
            .fetchedAt(Instant.now().minus(Duration.ofDays(5)))
            .build();

        when(sectorPerformanceSnapshotRepository.findByTickerIn(anyCollection()))
            .thenReturn(List.of(staleSnapshot));

        List<SectorPerformanceLiveResponse> results =
            sectorPerformanceSnapshotService.getSectorPerformanceFromDatabase();

        SectorPerformanceLiveResponse xle = results.stream()
            .filter(r -> r.ticker().equals("XLE"))
            .findFirst()
            .orElseThrow();
        assertThat(xle.lastClose()).isNull();
        assertThat(xle.dailyPct()).isNull();
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void collectionFetchesStockStateAndOhlcPerTickerEveryCycle() {
        when(sectorPerformanceSnapshotRepository.findByTickerIn(anyCollection())).thenReturn(List.of());
        when(unusualWhalesClient.getStockStateForStrataCollector(any())).thenReturn(stockState("100.00", "90.00"));
        when(unusualWhalesClient.getOhlcData(any(), eq("1d"), any(), eq("1Y"), any(), any()))
            .thenReturn(ohlcHistory());

        sectorPerformanceSnapshotService.collectAndStoreSnapshots();

        verify(unusualWhalesClient, times(SECTOR_ETFS.size())).getStockStateForStrataCollector(any());
        verify(unusualWhalesClient, times(SECTOR_ETFS.size())).getOhlcData(any(), eq("1d"), any(), eq("1Y"), any(), any());
        // dailyPct = (100 - 90) / 90, pct30d = (100 - 95) / 95, pct6m = (100 - 80) / 80
        verify(sectorPerformanceSnapshotRepository).upsert(
            eq("XLK"),
            eq(new BigDecimal("100.00")),
            eq(new BigDecimal("90.00")),
            doubleThat(pct -> Math.abs(pct - 11.111111) < 1e-4),
            doubleThat(pct -> Math.abs(pct - 5.263158) < 1e-4),
            doubleThat(pct -> Math.abs(pct - 25.0) < 1e-4),
            eq(1_000_000L),
            any(),
            eq(new BigDecimal("95.00")),
            eq(new BigDecimal("80.00")),
            any(), any()
        );
    }

    @Test
    void collectionKeepsPreviousSnapshotWhenStockStateFetchFails() {
        when(sectorPerformanceSnapshotRepository.findByTickerIn(anyCollection())).thenReturn(List.of());
        when(unusualWhalesClient.getStockStateForStrataCollector(any()))
            .thenThrow(new RuntimeException("upstream down"));

        sectorPerformanceSnapshotService.collectAndStoreSnapshots();

        verify(sectorPerformanceSnapshotRepository, never()).upsert(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void collectionFallsBackToStoredBaselinesWhenOhlcFetchFails() {
        SectorPerformanceSnapshot existing = SectorPerformanceSnapshot.builder()
            .ticker("XLK")
            .baseline30dClose(new BigDecimal("95.00"))
            .baseline6mClose(new BigDecimal("80.00"))
            .baselineDate(LocalDate.of(2026, 7, 7))
            .fetchedAt(Instant.now())
            .build();
        when(sectorPerformanceSnapshotRepository.findByTickerIn(anyCollection())).thenReturn(List.of(existing));
        when(unusualWhalesClient.getStockStateForStrataCollector(any())).thenReturn(stockState("100.00", "90.00"));
        when(unusualWhalesClient.getOhlcData(any(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("ohlc down"));

        sectorPerformanceSnapshotService.collectAndStoreSnapshots();

        verify(sectorPerformanceSnapshotRepository).upsert(
            eq("XLK"),
            eq(new BigDecimal("100.00")),
            eq(new BigDecimal("90.00")),
            any(),
            doubleThat(pct -> Math.abs(pct - 5.263158) < 1e-4),
            doubleThat(pct -> Math.abs(pct - 25.0) < 1e-4),
            eq(1_000_000L),
            any(),
            eq(new BigDecimal("95.00")),
            eq(new BigDecimal("80.00")),
            any(), any()
        );
    }

    private StockStateResponse stockState(String close, String prevClose) {
        return new StockStateResponse(new StockStateResponse.StockStateData(
            close, prevClose, "2026-07-07T15:00:00Z",
            5_000L, 1_000_000L, "95.00", "101.00", "94.00", "market"));
    }

    /**
     * Daily OHLC history whose 30-day baseline close resolves to 95.00 and
     * 6-month baseline close resolves to 80.00.
     */
    private UnusualWhalesOhlcDto ohlcHistory() {
        return new UnusualWhalesOhlcDto(List.of(
            ohlcItem("2026-01-08", "80.00"),
            ohlcItem("2026-06-08", "95.00"),
            ohlcItem("2026-07-06", "99.00")
        ));
    }

    private UnusualWhalesOhlcDto.OhlcItem ohlcItem(String date, String close) {
        return new UnusualWhalesOhlcDto.OhlcItem(
            null, null, date, null, null, null, close, null, null, "r");
    }
}
