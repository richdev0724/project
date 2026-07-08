package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.repository.DailyStockStateSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyStockStateSnapshotServiceTest {

    @Mock
    private DailyStockStateSnapshotRepository dailyStockStateSnapshotRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private DailyStockStateSnapshotService dailyStockStateSnapshotService;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(dailyStockStateSnapshotService, "tickers", List.of("SPY", "XLK"));
        ReflectionTestUtils.setField(dailyStockStateSnapshotService, "retentionDays", 4);
    }

    @Test
    void collectionStoresOneRowPerTickerAndPurgesOldRows() {
        when(unusualWhalesClient.getStockStateForStrataCollector(anyString()))
            .thenReturn(new StockStateResponse(new StockStateResponse.StockStateData(
                "156.00", "150.00", "2026-07-06T13:30:00Z", 1000L, 5000L,
                "151.00", "157.00", "149.00", "market")));
        when(dailyStockStateSnapshotRepository.deleteBySnapshotDateBefore(any())).thenReturn(3L);

        dailyStockStateSnapshotService.collectAndStoreSnapshots();

        LocalDate todayEt = LocalDate.now(ZoneId.of("America/New_York"));
        verify(unusualWhalesClient, times(2)).getStockStateForStrataCollector(anyString());
        verify(dailyStockStateSnapshotRepository).upsert(
            eq("SPY"), eq(todayEt),
            eq(new BigDecimal("156.00")), eq(new BigDecimal("150.00")),
            eq(new BigDecimal("151.00")), eq(new BigDecimal("157.00")), eq(new BigDecimal("149.00")),
            eq(1000L), eq(5000L),
            eq("2026-07-06T13:30:00Z"), eq("market"),
            any(Instant.class)
        );
        verify(dailyStockStateSnapshotRepository).deleteBySnapshotDateBefore(todayEt.minusDays(4));
    }

    @Test
    void catchUpSkipsWhenTodayRowsAreComplete() {
        when(dailyStockStateSnapshotRepository.existsByFetchedAtAfter(any())).thenReturn(true);
        // Lenient: only reached on weekdays after 9:30 AM ET (time-dependent).
        lenient().when(dailyStockStateSnapshotRepository.findLatestByTickerIn(any()))
            .thenReturn(List.of(
                snapshotFor("SPY"),
                snapshotFor("XLK")
            ));

        dailyStockStateSnapshotService.collectIfMissing();

        verify(unusualWhalesClient, never()).getStockStateForStrataCollector(anyString());
    }

    @Test
    void catchUpCollectsOnColdStart() {
        when(dailyStockStateSnapshotRepository.existsByFetchedAtAfter(any())).thenReturn(false);
        when(unusualWhalesClient.getStockStateForStrataCollector(anyString()))
            .thenReturn(new StockStateResponse(new StockStateResponse.StockStateData(
                "156.00", "150.00", null, 1000L, 5000L,
                "151.00", "157.00", "149.00", "market")));
        when(dailyStockStateSnapshotRepository.deleteBySnapshotDateBefore(any())).thenReturn(0L);

        dailyStockStateSnapshotService.collectIfMissing();

        verify(unusualWhalesClient, times(2)).getStockStateForStrataCollector(anyString());
    }

    private com.marketmono.entity.DailyStockStateSnapshot snapshotFor(String ticker) {
        return com.marketmono.entity.DailyStockStateSnapshot.builder()
            .ticker(ticker)
            .snapshotDate(LocalDate.now(ZoneId.of("America/New_York")))
            .fetchedAt(Instant.now())
            .build();
    }
}
