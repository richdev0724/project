package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.entity.SpotGexExposure;
import com.marketmono.exception.RateLimitExceededException;
import com.marketmono.service.spotgex.SpotGexStreamBucketPersistence;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexCollectionServiceTest {

    @Mock
    private SpotGexCollectionProperties properties;

    @Mock
    private CollectionConfigService collectionConfigService;

    @Mock
    private SpotGexFetchService spotGexFetchService;

    @Mock
    private SpotGexExposureBatchPersistence batchPersistence;

    @Mock
    private SpotGexRestSnapshotPersistence restSnapshotPersistence;

    @Mock
    private SpotGexBroadcaster spotGexBroadcaster;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private RateLimitService rateLimitService;

    @Spy
    private SpotGexTickerSourcePolicy tickerSourcePolicy = new SpotGexTickerSourcePolicy(new StrataTickerFamilyResolver());

    @InjectMocks
    private SpotGexCollectionService spotGexCollectionService;

    @Test
    void shouldCollectConfiguredTickersAndPersistRows() {
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 4, 17));
        SpotExposureByStrikeDto row = sampleRow("SPY", new BigDecimal("500.0000"));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(spotGexFetchService.getExpirationDates("SPY", null)).thenReturn(expirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", expirations, null, 500, 0L))
            .thenReturn(List.of(row));
        when(batchPersistence.persistBatch(anyList()))
            .thenReturn(new SpotGexExposureBatchPersistence.BatchPersistResult(1, 1, 0));

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectAll();

        assertThat(result.configuredTickers()).isEqualTo(1);
        assertThat(result.successfulTickers()).isEqualTo(1);
        assertThat(result.insertedRows()).isEqualTo(1);
        assertThat(result.abortedDueToRateLimit()).isFalse();

        ArgumentCaptor<List<SpotGexExposure>> exposureCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchPersistence).persistBatch(exposureCaptor.capture());
        assertThat(exposureCaptor.getValue()).hasSize(1);
        SpotGexExposure exposure = exposureCaptor.getValue().getFirst();
        assertThat(exposure.getTicker()).isEqualTo("SPY");
        assertThat(exposure.getExpiry()).isEqualTo(LocalDate.of(2026, 4, 17));
        assertThat(exposure.getStrike()).isEqualByComparingTo("500.0000");
        verify(spotGexBroadcaster).broadcastTickerSnapshot(eq("SPY"), eq(exposure.getCollectedAt()), anyList());
    }

    @Test
    void shouldCollectConfiguredTickersInBudgetedRotatingBatches() {
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 4, 17));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ", "IWM"));
        when(properties.getMaxTickersPerCycle()).thenReturn(2);
        when(properties.getCoreTickers()).thenReturn(List.of("SPY", "QQQ"));
        when(properties.getCoreTickerIntervalMs()).thenReturn(60_000L);
        when(properties.getInterTickerDelayMs()).thenReturn(0L);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(spotGexFetchService.getExpirationDates("SPY", null)).thenReturn(expirations);
        when(spotGexFetchService.getExpirationDates("QQQ", null)).thenReturn(expirations);
        when(spotGexFetchService.getExpirationDates("IWM", null)).thenReturn(expirations);
        when(spotGexFetchService.fetchAllSpotExposurePages(eq("SPY"), eq(expirations), eq(null), eq(500), eq(0L)))
            .thenReturn(List.of(sampleRow("SPY", new BigDecimal("500.0000"))));
        when(spotGexFetchService.fetchAllSpotExposurePages(eq("QQQ"), eq(expirations), eq(null), eq(500), eq(0L)))
            .thenReturn(List.of(sampleRow("QQQ", new BigDecimal("450.0000"))));
        when(spotGexFetchService.fetchAllSpotExposurePages(eq("IWM"), eq(expirations), eq(null), eq(500), eq(0L)))
            .thenReturn(List.of(sampleRow("IWM", new BigDecimal("220.0000"))));
        when(batchPersistence.persistBatch(anyList()))
            .thenReturn(new SpotGexExposureBatchPersistence.BatchPersistResult(1, 1, 0));

        SpotGexCollectionService.CollectionCycleResult first = spotGexCollectionService.collectAll();
        SpotGexCollectionService.CollectionCycleResult second = spotGexCollectionService.collectAll();

        assertThat(first.configuredTickers()).isEqualTo(3);
        assertThat(first.successfulTickers()).isEqualTo(2);
        assertThat(second.configuredTickers()).isEqualTo(3);
        assertThat(second.successfulTickers()).isEqualTo(1);
        verify(spotGexFetchService, times(1)).getExpirationDates("SPY", null);
        verify(spotGexFetchService, times(1)).getExpirationDates("QQQ", null);
        verify(spotGexFetchService, times(1)).getExpirationDates("IWM", null);
    }

    @Test
    void shouldAbortRemainingCycleWhenRateLimitExceeded() {
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ"));
        when(spotGexFetchService.getExpirationDates("SPY", null))
            .thenThrow(new RateLimitExceededException("limit reached"));

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectAll();

        assertThat(result.abortedDueToRateLimit()).isTrue();
        assertThat(result.failedTickers()).isEqualTo(1);
        assertThat(result.failedTickersList()).containsExactly("SPY");
        verify(spotGexFetchService, never()).getExpirationDates("QQQ", null);
    }

    @Test
    void shouldAbortRemainingCycleWhenUwBudgetIsAboveTarget() {
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 4, 17));
        SpotExposureByStrikeDto row = sampleRow("SPY", new BigDecimal("500.0000"));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ"));
        when(properties.isAbortWhenUwBudgetAboveTarget()).thenReturn(true);
        when(properties.getInterTickerDelayMs()).thenReturn(0L);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(unusualWhalesClient.isRequestBudgetAboveTarget()).thenReturn(false, true);
        when(spotGexFetchService.getExpirationDates("SPY", null)).thenReturn(expirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", expirations, null, 500, 0L))
            .thenReturn(List.of(row));
        when(batchPersistence.persistBatch(anyList()))
            .thenReturn(new SpotGexExposureBatchPersistence.BatchPersistResult(1, 1, 0));

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectAll();

        assertThat(result.abortedDueToRateLimit()).isTrue();
        assertThat(result.successfulTickers()).isEqualTo(1);
        assertThat(result.failedTickers()).isZero();
        verify(spotGexFetchService, never()).getExpirationDates("QQQ", null);
    }

    @Test
    void shouldSkipAllSpotGexCollectionWhenDailyProjectionExceedsStopThreshold() {
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ"));
        when(properties.getDailyProjectionStopThreshold()).thenReturn(150_000);
        when(rateLimitService.isDailyProjectionAtOrAbove(150_000)).thenReturn(true);

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectAll();

        assertThat(result.configuredTickers()).isEqualTo(2);
        assertThat(result.successfulTickers()).isZero();
        assertThat(result.failedTickers()).isZero();
        assertThat(result.abortedDueToRateLimit()).isTrue();
        verify(spotGexFetchService, never()).getExpirationDates("SPY", null);
        verify(spotGexFetchService, never()).getExpirationDates("QQQ", null);
    }

    @Test
    void shouldCollectOnlyCoreTickersWhenDailyProjectionExceedsWarningThreshold() {
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 4, 17));
        SpotExposureByStrikeDto row = sampleRow("SPY", new BigDecimal("500.0000"));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "AAPL"));
        when(properties.getCoreTickers()).thenReturn(List.of("SPY"));
        when(properties.getDailyProjectionStopThreshold()).thenReturn(150_000);
        when(properties.getDailyProjectionWarningThreshold()).thenReturn(120_000);
        when(properties.getMaxTickersPerCycle()).thenReturn(2);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(rateLimitService.isDailyProjectionAtOrAbove(150_000)).thenReturn(false);
        when(rateLimitService.isDailyProjectionAtOrAbove(120_000)).thenReturn(true);
        when(spotGexFetchService.getExpirationDates("SPY", null)).thenReturn(expirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", expirations, null, 500, 0L))
            .thenReturn(List.of(row));
        when(batchPersistence.persistBatch(anyList()))
            .thenReturn(new SpotGexExposureBatchPersistence.BatchPersistResult(1, 1, 0));

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectAll();

        assertThat(result.configuredTickers()).isEqualTo(2);
        assertThat(result.successfulTickers()).isEqualTo(1);
        assertThat(result.abortedDueToRateLimit()).isFalse();
        verify(spotGexFetchService).getExpirationDates("SPY", null);
        verify(spotGexFetchService, never()).getExpirationDates("AAPL", null);
    }

    @Test
    void shouldCollectRestOnlyFamilyRootsIntoStreamReadModelInStreamMode() {
        List<LocalDate> vixwExpirations = List.of(LocalDate.of(2026, 7, 1));
        List<LocalDate> vixExpirations = List.of(LocalDate.of(2026, 7, 22));
        SpotExposureByStrikeDto vixRow = sampleRow("VIX", new BigDecimal("20.0000"));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("VIXW", "SPY"));
        when(properties.getInterTickerDelayMs()).thenReturn(0L);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(spotGexFetchService.getExpirationDates("VIXW", null)).thenReturn(vixwExpirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("VIXW", vixwExpirations, null, 500, 0L))
            .thenReturn(List.of());
        when(spotGexFetchService.getExpirationDates("VIX", null)).thenReturn(vixExpirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("VIX", vixExpirations, null, 500, 0L))
            .thenReturn(List.of(vixRow));
        when(restSnapshotPersistence.persistSnapshot(eq("VIX"), eq(List.of(vixRow)), any(Instant.class)))
            .thenReturn(new SpotGexStreamBucketPersistence.BatchPersistResult(1, 1, 0));

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectRestOnlyTickers();

        assertThat(result.configuredTickers()).isEqualTo(2);
        assertThat(result.successfulTickers()).isEqualTo(2);
        assertThat(result.attemptedRows()).isEqualTo(1);
        assertThat(result.insertedRows()).isEqualTo(1);
        verify(spotGexFetchService).getExpirationDates("VIXW", null);
        verify(spotGexFetchService).getExpirationDates("VIX", null);
        verify(spotGexFetchService, never()).getExpirationDates("SPY", null);
        verify(restSnapshotPersistence).persistSnapshot(eq("VIX"), eq(List.of(vixRow)), any(Instant.class));
        verify(batchPersistence, never()).persistBatch(anyList());
    }

    @Test
    void shouldSkipRestOnlyRootsUntilRestOnlyCadenceExpires() {
        List<LocalDate> spxwExpirations = List.of(LocalDate.of(2026, 7, 1));
        List<LocalDate> spxExpirations = List.of(LocalDate.of(2026, 7, 17));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPXW"));
        when(properties.getRestOnlyIntervalMs()).thenReturn(180_000L);
        when(properties.getInterTickerDelayMs()).thenReturn(0L);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(spotGexFetchService.getExpirationDates("SPXW", null)).thenReturn(spxwExpirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPXW", spxwExpirations, null, 500, 0L))
            .thenReturn(List.of());
        when(spotGexFetchService.getExpirationDates("SPX", null)).thenReturn(spxExpirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPX", spxExpirations, null, 500, 0L))
            .thenReturn(List.of());

        SpotGexCollectionService.CollectionCycleResult first = spotGexCollectionService.collectRestOnlyTickers();
        SpotGexCollectionService.CollectionCycleResult second = spotGexCollectionService.collectRestOnlyTickers();

        assertThat(first.configuredTickers()).isEqualTo(1);
        assertThat(first.successfulTickers()).isEqualTo(2);
        assertThat(second.configuredTickers()).isEqualTo(1);
        assertThat(second.successfulTickers()).isZero();
        assertThat(second.failedTickers()).isZero();
        verify(spotGexFetchService, times(1)).getExpirationDates("SPXW", null);
        verify(spotGexFetchService, times(1)).getExpirationDates("SPX", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCollectRestOnlyRootsAfterRestOnlyCadenceExpires() {
        List<LocalDate> spxwExpirations = List.of(LocalDate.of(2026, 7, 1));
        List<LocalDate> spxExpirations = List.of(LocalDate.of(2026, 7, 17));
        Instant previousAttempt = Instant.now().minusMillis(181_000L);
        Map<String, SpotGexCollectionService.TickerRuntimeStatus> runtimeStatusByTicker =
            (Map<String, SpotGexCollectionService.TickerRuntimeStatus>) ReflectionTestUtils.getField(
                spotGexCollectionService,
                "runtimeStatusByTicker"
            );
        assertThat(runtimeStatusByTicker).isNotNull();
        runtimeStatusByTicker.put("SPXW", restOnlyStatus(previousAttempt));
        runtimeStatusByTicker.put("SPX", restOnlyStatus(previousAttempt));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPXW"));
        when(properties.getRestOnlyIntervalMs()).thenReturn(180_000L);
        when(properties.getInterTickerDelayMs()).thenReturn(0L);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(spotGexFetchService.getExpirationDates("SPXW", null)).thenReturn(spxwExpirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPXW", spxwExpirations, null, 500, 0L))
            .thenReturn(List.of());
        when(spotGexFetchService.getExpirationDates("SPX", null)).thenReturn(spxExpirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPX", spxExpirations, null, 500, 0L))
            .thenReturn(List.of());

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectRestOnlyTickers();

        assertThat(result.configuredTickers()).isEqualTo(1);
        assertThat(result.successfulTickers()).isEqualTo(2);
        verify(spotGexFetchService).getExpirationDates("SPXW", null);
        verify(spotGexFetchService).getExpirationDates("SPX", null);
    }

    @Test
    void shouldRecordRestOnlyCollectionDetailMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(spotGexCollectionService, "meterRegistry", registry);
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 7, 1));
        List<SpotExposureByStrikeDto> rows = List.of(
            sampleRow("SPXW", new BigDecimal("5000.0000")),
            sampleRow("SPXW", new BigDecimal("5005.0000")),
            sampleRow("SPXW", new BigDecimal("5010.0000"))
        );

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPXW"));
        when(properties.getInterTickerDelayMs()).thenReturn(0L);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(2);
        when(spotGexFetchService.getExpirationDates("SPXW", null)).thenReturn(expirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("SPXW", expirations, null, 2, 0L))
            .thenReturn(rows);
        when(spotGexFetchService.getExpirationDates("SPX", null)).thenReturn(List.of());
        when(restSnapshotPersistence.persistSnapshot(eq("SPXW"), anyList(), any(Instant.class)))
            .thenReturn(new SpotGexStreamBucketPersistence.BatchPersistResult(3, 2, 1));

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectRestOnlyTickers();

        assertThat(result.successfulTickers()).isEqualTo(2);
        assertThat(registry.get("marketmono.spot.gex.collection.duration")
            .tag("ticker", "SPXW")
            .tag("tier", "rest_only")
            .tag("result", "success")
            .timer()
            .count()).isEqualTo(1);
        assertThat(registry.get("marketmono.spot.gex.collection.fetched.pages")
            .tag("ticker", "SPXW")
            .tag("tier", "rest_only")
            .tag("result", "success")
            .summary()
            .totalAmount()).isEqualTo(2.0);
        assertThat(registry.get("marketmono.spot.gex.collection.rows")
            .tag("ticker", "SPXW")
            .tag("tier", "rest_only")
            .tag("result", "success")
            .tag("row_type", "attempted")
            .summary()
            .totalAmount()).isEqualTo(3.0);
        assertThat(registry.get("marketmono.spot.gex.collection.rows")
            .tag("ticker", "SPXW")
            .tag("tier", "rest_only")
            .tag("result", "success")
            .tag("row_type", "inserted")
            .summary()
            .totalAmount()).isEqualTo(2.0);
        assertThat(registry.get("marketmono.spot.gex.collection.rows")
            .tag("ticker", "SPXW")
            .tag("tier", "rest_only")
            .tag("result", "success")
            .tag("row_type", "skipped")
            .summary()
            .totalAmount()).isEqualTo(1.0);
    }

    @Test
    void shouldContinueWhenSingleTickerFailsWithRecoverableError() {
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 4, 17));
        SpotExposureByStrikeDto row = sampleRow("QQQ", new BigDecimal("450.0000"));

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ"));
        when(properties.getInterTickerDelayMs()).thenReturn(0L);
        when(properties.getInterPageDelayMs()).thenReturn(0L);
        when(properties.getPageSize()).thenReturn(500);
        when(spotGexFetchService.getExpirationDates("SPY", null)).thenThrow(new IllegalStateException("boom"));
        when(spotGexFetchService.getExpirationDates("QQQ", null)).thenReturn(expirations);
        when(spotGexFetchService.fetchAllSpotExposurePages("QQQ", expirations, null, 500, 0L))
            .thenReturn(List.of(row));
        when(batchPersistence.persistBatch(anyList()))
            .thenReturn(new SpotGexExposureBatchPersistence.BatchPersistResult(1, 1, 0));

        SpotGexCollectionService.CollectionCycleResult result = spotGexCollectionService.collectAll();

        assertThat(result.successfulTickers()).isEqualTo(1);
        assertThat(result.failedTickers()).isEqualTo(1);
        assertThat(result.failedTickersList()).containsExactly("SPY");
        verify(spotGexFetchService).getExpirationDates("QQQ", null);
    }

    private SpotExposureByStrikeDto sampleRow(String ticker, BigDecimal strike) {
        return new SpotExposureByStrikeDto(
            ticker,
            BigDecimal.ONE,
            BigDecimal.TWO,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            LocalDate.of(2026, 4, 6),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("502.1200"),
            BigDecimal.ZERO,
            strike,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDate.of(2026, 4, 17)
        );
    }

    private SpotGexCollectionService.TickerRuntimeStatus restOnlyStatus(Instant lastAttemptAt) {
        return new SpotGexCollectionService.TickerRuntimeStatus(
            lastAttemptAt,
            lastAttemptAt,
            null,
            0,
            0,
            0,
            0,
            true
        );
    }
}
