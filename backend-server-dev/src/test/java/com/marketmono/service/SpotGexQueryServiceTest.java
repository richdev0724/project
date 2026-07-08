package com.marketmono.service;

import com.marketmono.dto.SpotGexCollectionStatusDto;
import com.marketmono.dto.SpotGexSnapshotDto;
import com.marketmono.dto.SpotGexStreamStatusDto;
import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.entity.SpotGexExposure;
import com.marketmono.repository.SpotGexExposureRepository;
import com.marketmono.repository.SpotGexStreamBucketRepository;
import com.marketmono.service.spotgex.SpotGexStreamHealth;
import com.marketmono.service.spotgex.SpotGexStreamIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexQueryServiceTest {

    @Mock
    private SpotGexExposureRepository repository;

    @Mock
    private CollectionConfigService collectionConfigService;

    @Mock
    private SpotGexCollectionService spotGexCollectionService;

    @Mock
    private SpotGexFetchService spotGexFetchService;

    @Mock
    private SpotGexStreamBucketRepository streamBucketRepository;

    @Mock
    private SpotGexCollectionProperties properties;

    @Mock
    private SpotGexStreamIngestionService streamIngestionService;

    @Spy
    private StrataTickerFamilyResolver strataTickerFamilyResolver = new StrataTickerFamilyResolver();

    @Spy
    private SpotGexTickerSourcePolicy tickerSourcePolicy = new SpotGexTickerSourcePolicy(new StrataTickerFamilyResolver());

    @InjectMocks
    private SpotGexQueryService spotGexQueryService;

    @Test
    void shouldReturnLatestSnapshotForTicker() {
        Instant collectedAt = Instant.parse("2026-04-06T14:31:00Z");
        SpotGexExposure exposure = SpotGexExposure.builder()
            .ticker("SPY")
            .expiry(LocalDate.of(2026, 4, 17))
            .strike(new BigDecimal("500.0000"))
            .collectedAt(collectedAt)
            .price(new BigDecimal("502.1200"))
            .build();

        when(repository.findLatestCollectedAtByTicker("SPY")).thenReturn(Optional.of(collectedAt));
        when(repository.findSnapshot("SPY", collectedAt, null, null, null)).thenReturn(List.of(exposure));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", null, null, null);

        assertThat(result.collectedAt()).isEqualTo(collectedAt);
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().ticker()).isEqualTo("SPY");
        assertThat(result.data().getFirst().snapshotAt()).isEqualTo(collectedAt);
        verifyNoInteractions(spotGexFetchService);
    }

    @Test
    void shouldUseLiveFallbackWhenNoPersistedLatestSnapshotExists() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto inRangeRow = sampleLiveRow("LLY", expiry, new BigDecimal("910.0000"));
        SpotExposureByStrikeDto belowRangeRow = sampleLiveRow("LLY", expiry, new BigDecimal("870.0000"));

        when(repository.findLatestCollectedAtByTicker("LLY")).thenReturn(Optional.empty());
        when(spotGexFetchService.getExpirationDates("LLY", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("LLY", List.of(expiry)))
            .thenReturn(List.of(belowRangeRow, inRangeRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("lly", null, new BigDecimal("900"), new BigDecimal("950"));

        assertThat(result.collectedAt()).isNotNull();
        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isFalse();
        assertThat(result.code()).isNull();
        assertThat(result.data()).hasSize(1);
        SpotGexSnapshotDto snapshot = result.data().getFirst();
        assertThat(snapshot.ticker()).isEqualTo("LLY");
        assertThat(snapshot.expiry()).isEqualTo(expiry);
        assertThat(snapshot.strike()).isEqualByComparingTo("910.0000");
        assertThat(snapshot.marketDate()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(snapshot.sourceTime()).isEqualTo(Instant.parse("2026-06-08T14:30:00Z"));
        assertThat(snapshot.callGammaOi()).isEqualByComparingTo("10.1000");
        verify(repository, never()).findSnapshot(any(), any(), any(), any(), any());
        verify(spotGexFetchService).getExpirationDates("LLY", null);
        verify(spotGexFetchService).fetchAllSpotExposurePages("LLY", List.of(expiry));
    }

    @Test
    void shouldReturnLatestSnapshotFromStreamBucketsInStreamMode() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart,
            "250.0000",
            "-75.0000"
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, new BigDecimal("700"), new BigDecimal("750")))
            .thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, new BigDecimal("700"), new BigDecimal("750"));

        assertThat(result.collectedAt()).isEqualTo(bucketStart);
        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isFalse();
        assertThat(result.latestBucketStart()).isEqualTo(bucketStart);
        assertThat(result.latestBucketLagMillis()).isBetween(0L, 120_000L);
        assertThat(result.streamStale()).isFalse();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().ticker()).isEqualTo("SPY");
        assertThat(result.data().getFirst().snapshotAt()).isEqualTo(bucketStart);
        assertThat(result.data().getFirst().callGammaOi()).isEqualByComparingTo("250.0000");
        verifyNoInteractions(spotGexFetchService);
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldReturnRestOnlyLatestSnapshotFromPersistedFamilyRootBeforeStreamSubscriptionChecks() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 7, 17);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRowWithDelta(
            "SPX",
            expiry,
            new BigDecimal("6200.0000"),
            bucketStart
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPXW")).thenReturn(Optional.empty());
        when(streamBucketRepository.findLatestBucketStartByTicker("SPX")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPX", bucketStart, expiry, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spxw", expiry, null, null);

        assertThat(result.source()).isEqualTo("rest");
        assertThat(result.degraded()).isFalse();
        assertThat(result.code()).isNull();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().ticker()).isEqualTo("SPXW");
        assertThat(result.latestBucketStart()).isEqualTo(bucketStart);
        assertThat(result.streamSubscribed()).isFalse();
        verifyNoInteractions(spotGexFetchService);
    }

    @Test
    void shouldEnrichMissingStreamDeltaFieldsFromRestWhenEnabled() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        BigDecimal strike = new BigDecimal("725.0000");
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            strike,
            bucketStart,
            "250.0000",
            "-75.0000"
        );
        SpotExposureByStrikeDto restRow = sampleLiveRow("SPY", expiry, strike);

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", List.of(expiry))).thenReturn(List.of(restRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isFalse();
        assertThat(result.data()).hasSize(1);
        SpotGexSnapshotDto snapshot = result.data().getFirst();
        assertThat(snapshot.callGammaOi()).isEqualByComparingTo("250.0000");
        assertThat(snapshot.putGammaOi()).isEqualByComparingTo("-75.0000");
        assertThat(snapshot.callDeltaOi()).isEqualByComparingTo("17.1000");
        assertThat(snapshot.putDeltaOi()).isEqualByComparingTo("1.1000");
        assertThat(snapshot.callDeltaVol()).isEqualByComparingTo("2.1000");
        assertThat(snapshot.putDeltaVol()).isEqualByComparingTo("13.1000");
        assertThat(snapshot.callDeltaBid()).isEqualByComparingTo("18.1000");
        assertThat(snapshot.callDeltaAsk()).isEqualByComparingTo("29.1000");
        assertThat(snapshot.putDeltaBid()).isEqualByComparingTo("26.1000");
        assertThat(snapshot.putDeltaAsk()).isEqualByComparingTo("24.1000");
        verify(spotGexFetchService).fetchAllSpotExposurePages("SPY", List.of(expiry));
        verify(spotGexFetchService, never()).getExpirationDates(any(), any());
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldEnrichMissingStreamStrataAndFlowFieldsFromRestWhenEnabled() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        BigDecimal strike = new BigDecimal("725.0000");
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            strike,
            bucketStart,
            "250.0000",
            "-75.0000"
        );
        SpotExposureByStrikeDto restRow = sampleLiveRow("SPY", expiry, strike);

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", List.of(expiry))).thenReturn(List.of(restRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isFalse();
        assertThat(result.data()).hasSize(1);
        SpotGexSnapshotDto snapshot = result.data().getFirst();
        assertThat(snapshot.callGammaOi()).isEqualByComparingTo("250.0000");
        assertThat(snapshot.putGammaOi()).isEqualByComparingTo("-75.0000");
        assertThat(snapshot.callGammaVol()).isEqualByComparingTo("11.1000");
        assertThat(snapshot.putGammaVol()).isEqualByComparingTo("7.1000");
        assertThat(snapshot.callGammaBid()).isEqualByComparingTo("27.1000");
        assertThat(snapshot.callGammaAsk()).isEqualByComparingTo("19.1000");
        assertThat(snapshot.putGammaBid()).isEqualByComparingTo("33.1000");
        assertThat(snapshot.putGammaAsk()).isEqualByComparingTo("22.1000");
        verify(spotGexFetchService).fetchAllSpotExposurePages("SPY", List.of(expiry));
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldNotCallRestDeltaEnrichmentWhenStreamAlreadyHasDeltaFields() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().callDeltaOi()).isEqualByComparingTo("101.0000");
        assertThat(result.data().getFirst().putDeltaOi()).isEqualByComparingTo("-99.0000");
        verifyNoInteractions(spotGexFetchService);
    }

    @Test
    void shouldCacheLatestStreamSnapshotForRepeatedRequestsWithinSameBucket() {
        enableLatestSnapshotCache();
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(first.data()).hasSize(1);
        assertThat(second.data()).hasSize(1);
        assertThat(second.collectedAt()).isEqualTo(first.collectedAt());
        verify(streamBucketRepository, times(2)).findLatestBucketStartByTicker("SPY");
        verify(streamBucketRepository, times(1)).findSnapshot("SPY", bucketStart, expiry, null, null);
        verifyNoInteractions(spotGexFetchService);
    }

    @Test
    void shouldShareLatestStreamSnapshotCacheForScaleEquivalentStrikeFilters() {
        enableLatestSnapshotCache();
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot(
            eq("SPY"),
            eq(bucketStart),
            eq(expiry),
            any(BigDecimal.class),
            any(BigDecimal.class)
        )).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot(
                "spy",
                expiry,
                new BigDecimal("700"),
                new BigDecimal("750.0000")
            );
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot(
                "spy",
                expiry,
                new BigDecimal("700.0000"),
                new BigDecimal("750")
            );

        assertThat(first.data()).hasSize(1);
        assertThat(second.data()).hasSize(1);
        assertThat(second.collectedAt()).isEqualTo(first.collectedAt());
        verify(streamBucketRepository, times(1)).findSnapshot(
            eq("SPY"),
            eq(bucketStart),
            eq(expiry),
            any(BigDecimal.class),
            any(BigDecimal.class)
        );
    }

    @Test
    void shouldCacheRestDeltaEnrichmentForLatestStreamSnapshot() {
        enableLatestSnapshotCache();
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        BigDecimal strike = new BigDecimal("725.0000");
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            strike,
            bucketStart,
            "250.0000",
            "-75.0000"
        );
        SpotExposureByStrikeDto restRow = sampleLiveRow("SPY", expiry, strike);

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", List.of(expiry))).thenReturn(List.of(restRow));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(first.data().getFirst().callDeltaOi()).isEqualByComparingTo("17.1000");
        assertThat(second.data().getFirst().callDeltaOi()).isEqualByComparingTo("17.1000");
        verify(streamBucketRepository, times(1)).findSnapshot("SPY", bucketStart, expiry, null, null);
        verify(spotGexFetchService, times(1)).fetchAllSpotExposurePages("SPY", List.of(expiry));
    }

    @Test
    void shouldCacheStaleLatestStreamSnapshotWithData() {
        enableLatestSnapshotCache();
        Instant bucketStart = Instant.now().minusMillis(180_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(first.degraded()).isTrue();
        assertThat(second.degraded()).isTrue();
        assertThat(first.code()).isEqualTo("SPOT_GEX_STREAM_STALE");
        assertThat(second.code()).isEqualTo("SPOT_GEX_STREAM_STALE");
        verify(streamBucketRepository, times(1)).findSnapshot("SPY", bucketStart, expiry, null, null);
    }

    @Test
    void shouldUseLiveFallbackWhenStreamLatestSnapshotQueryFails() {
        enableLatestSnapshotCache();
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto liveRow = sampleLiveRow("SPY", expiry, new BigDecimal("725.0000"));

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", true, 1, List.of("SPY")),
                Instant.now().minusMillis(30_000L),
                "SPY",
                null,
                bucketStart,
                0
            )
        );
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null))
            .thenThrow(new DataAccessResourceFailureException("canceling statement due to user request"));
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", List.of(expiry))).thenReturn(List.of(liveRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isFalse();
        assertThat(result.data()).hasSize(1);
        assertThat(readRecordComponent(result, "coverageMode")).isEqualTo("live_fallback");
        assertThat(readRecordComponent(result, "fallbackReason")).isEqualTo("stream_snapshot_query_failed");
        verify(streamBucketRepository, times(1)).findSnapshot("SPY", bucketStart, expiry, null, null);
        verify(spotGexFetchService).fetchAllSpotExposurePages("SPY", List.of(expiry));
    }

    @Test
    void shouldCoalesceConcurrentLatestStreamSnapshotLoads() throws Exception {
        enableLatestSnapshotCache();
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart
        );
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CountDownLatch bucketLookups = new CountDownLatch(2);

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenAnswer(invocation -> {
            bucketLookups.countDown();
            return Optional.of(bucketStart);
        });
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenAnswer(invocation -> {
            loaderStarted.countDown();
            assertThat(releaseLoader.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of(row);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SpotGexQueryService.LatestSnapshotResult> first = executor.submit(() ->
                spotGexQueryService.getLatestSnapshot("spy", expiry, null, null)
            );
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<SpotGexQueryService.LatestSnapshotResult> second = executor.submit(() ->
                spotGexQueryService.getLatestSnapshot("spy", expiry, null, null)
            );

            assertThat(bucketLookups.await(5, TimeUnit.SECONDS)).isTrue();
            releaseLoader.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).data()).hasSize(1);
            assertThat(second.get(5, TimeUnit.SECONDS).data()).hasSize(1);
            verify(streamBucketRepository, times(1)).findSnapshot("SPY", bucketStart, expiry, null, null);
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRetryLatestStreamSnapshotAfterLoaderFailureClearsInFlight() {
        enableLatestSnapshotCache();
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null))
            .thenThrow(new IllegalStateException("database timeout"))
            .thenReturn(List.of(row));

        assertThatThrownBy(() -> spotGexQueryService.getLatestSnapshot("spy", expiry, null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("database timeout");

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.data()).hasSize(1);
        verify(streamBucketRepository, times(2)).findSnapshot("SPY", bucketStart, expiry, null, null);
    }

    @Test
    void shouldKeepLatestStreamSnapshotCacheEntriesSeparateByBucket() {
        enableLatestSnapshotCache();
        Instant firstBucket = Instant.now().minusMillis(60_000L);
        Instant secondBucket = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection firstRow = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            firstBucket
        );
        SpotGexExposureRepository.SpotGexHistoryProjection secondRow = streamRowWithDelta(
            "SPY",
            expiry,
            new BigDecimal("730.0000"),
            secondBucket
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY"))
            .thenReturn(Optional.of(firstBucket))
            .thenReturn(Optional.of(secondBucket));
        when(streamBucketRepository.findSnapshot("SPY", firstBucket, expiry, null, null)).thenReturn(List.of(firstRow));
        when(streamBucketRepository.findSnapshot("SPY", secondBucket, expiry, null, null)).thenReturn(List.of(secondRow));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(first.collectedAt()).isEqualTo(firstBucket);
        assertThat(second.collectedAt()).isEqualTo(secondBucket);
        assertThat(first.data().getFirst().strike()).isEqualByComparingTo("725.0000");
        assertThat(second.data().getFirst().strike()).isEqualByComparingTo("730.0000");
        verify(streamBucketRepository, times(1)).findSnapshot("SPY", firstBucket, expiry, null, null);
        verify(streamBucketRepository, times(1)).findSnapshot("SPY", secondBucket, expiry, null, null);
    }

    @Test
    void shouldCacheLatestPersistedSnapshotForRepeatedRequestsWithinSameCollectedAt() {
        enableLatestSnapshotCache();
        Instant collectedAt = Instant.parse("2026-06-17T14:35:00Z");
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotGexExposure exposure = SpotGexExposure.builder()
            .ticker("SPY")
            .expiry(expiry)
            .strike(new BigDecimal("550.0000"))
            .collectedAt(collectedAt)
            .price(new BigDecimal("548.1200"))
            .build();

        when(repository.findLatestCollectedAtByTicker("SPY")).thenReturn(Optional.of(collectedAt));
        when(repository.findSnapshot("SPY", collectedAt, expiry, null, null)).thenReturn(List.of(exposure));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(first.data()).hasSize(1);
        assertThat(second.data()).hasSize(1);
        assertThat(second.collectedAt()).isEqualTo(first.collectedAt());
        verify(repository, times(2)).findLatestCollectedAtByTicker("SPY");
        verify(repository, times(1)).findSnapshot("SPY", collectedAt, expiry, null, null);
    }

    @Test
    void shouldKeepStreamSnapshotWhenRestDeltaEnrichmentFails() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart,
            "250.0000",
            "-75.0000"
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.isStreamDeltaRestEnrichmentEnabled()).thenReturn(true);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));
        when(spotGexFetchService.fetchAllSpotExposurePages("SPY", List.of(expiry)))
            .thenThrow(new IllegalStateException("UW REST unavailable"));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isFalse();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().callGammaOi()).isEqualByComparingTo("250.0000");
        assertThat(result.data().getFirst().callDeltaOi()).isNull();
        verify(spotGexFetchService).fetchAllSpotExposurePages("SPY", List.of(expiry));
    }

    @Test
    void shouldReturnLatestStreamBucketSnapshotFromStandbyNode() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart,
            "250.0000",
            "-75.0000"
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                false,
                false,
                "backend-b-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", false, 0, List.of()),
                null,
                null,
                null,
                bucketStart,
                0
            )
        );
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isFalse();
        assertThat(result.data()).hasSize(1);
        assertThat(result.latestBucketStart()).isEqualTo(bucketStart);
        assertThat(result.streamConnected()).isFalse();
        assertThat(result.streamSubscribed()).isFalse();
        assertThat(result.streamStale()).isFalse();
        verifyNoInteractions(spotGexFetchService);
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldReturnLatestStreamBucketSnapshotWhenActiveOwnerHasNoFreshTransportRecord() {
        Instant bucketStart = Instant.now().minusMillis(30_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart,
            "250.0000",
            "-75.0000"
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", false, 26, List.of("SPY", "QQQ")),
                null,
                null,
                null,
                bucketStart,
                0
            )
        );
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isFalse();
        assertThat(result.data()).hasSize(1);
        assertThat(result.latestBucketStart()).isEqualTo(bucketStart);
        assertThat(result.streamConnected()).isFalse();
        assertThat(result.streamSubscribed()).isTrue();
        verifyNoInteractions(spotGexFetchService);
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldReturnStaleStreamDegradationMetadataWithLatestBucketData() {
        Instant bucketStart = Instant.now().minusMillis(180_000L);
        LocalDate expiry = LocalDate.of(2026, 6, 11);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            expiry,
            new BigDecimal("725.0000"),
            bucketStart,
            "250.0000",
            "-75.0000"
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", true, 1, List.of("SPY")),
                Instant.now().minusMillis(180_000L),
                "SPY",
                null,
                bucketStart,
                0
            )
        );
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(bucketStart));
        when(streamBucketRepository.findSnapshot("SPY", bucketStart, expiry, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", expiry, null, null);

        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_STREAM_STALE");
        assertThat(result.data()).hasSize(1);
        assertThat(result.latestBucketStart()).isEqualTo(bucketStart);
        assertThat(result.latestBucketLagMillis()).isGreaterThan(120_000L);
        assertThat(result.streamConnected()).isTrue();
        assertThat(result.streamSubscribed()).isTrue();
        assertThat(result.streamStale()).isTrue();
        verifyNoInteractions(spotGexFetchService);
    }

    @Test
    void shouldReturnDegradedStreamResultWithoutRestFallbackWhenNoStreamBucketExists() {
        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.empty());

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", null, null, null);

        assertThat(result.collectedAt()).isNull();
        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_STREAM_BUCKET_EMPTY");
        assertThat(result.data()).isEmpty();
        verifyNoInteractions(spotGexFetchService);
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldUseLiveFallbackForUnconfiguredTickerOnStandbyNode() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto liveRow = sampleLiveRow("IBM", expiry, new BigDecimal("280.0000"));

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ", "MSFT"));
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                false,
                false,
                "backend-b-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", false, 0, List.of()),
                null,
                null,
                null,
                null,
                0
            )
        );
        when(spotGexFetchService.getExpirationDates("IBM", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("IBM", List.of(expiry))).thenReturn(List.of(liveRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("ibm", null, null, null);

        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isFalse();
        assertThat(result.code()).isNull();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().ticker()).isEqualTo("IBM");
        assertThat(result.data().getFirst().strike()).isEqualByComparingTo("280.0000");
        verify(spotGexFetchService).getExpirationDates("IBM", null);
        verify(spotGexFetchService).fetchAllSpotExposurePages("IBM", List.of(expiry));
        verifyNoInteractions(streamBucketRepository);
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldUseLiveFallbackForUnconfiguredTickerWhenStreamOwnerTransportDisconnected() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto liveRow = sampleLiveRow("IBM", expiry, new BigDecimal("280.0000"));

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ", "MSFT"));
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", false, 26, List.of("SPY", "QQQ", "MSFT")),
                null,
                null,
                null,
                null,
                0
            )
        );
        when(spotGexFetchService.getExpirationDates("IBM", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("IBM", List.of(expiry))).thenReturn(List.of(liveRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("ibm", null, null, null);

        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isFalse();
        assertThat(result.code()).isNull();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().ticker()).isEqualTo("IBM");
        assertThat(readRecordComponent(result, "coverageMode")).isEqualTo("live_fallback");
        assertThat(readRecordComponent(result, "fallbackReason")).isEqualTo("outside_stream_coverage");
        verifyNoInteractions(streamBucketRepository);
        verify(repository, never()).findLatestCollectedAtByTicker(any());
    }

    @Test
    void shouldExposeLiveFallbackReasonMetadataForUnconfiguredTicker() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto liveRow = sampleLiveRow("IBM", expiry, new BigDecimal("280.0000"));

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ", "MSFT"));
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                false,
                false,
                "backend-b-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", false, 0, List.of()),
                null,
                null,
                null,
                null,
                0
            )
        );
        when(spotGexFetchService.getExpirationDates("IBM", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("IBM", List.of(expiry))).thenReturn(List.of(liveRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("ibm", null, null, null);

        assertThat(recordComponentNames()).contains("coverageMode", "fallbackReason");
        assertThat(readRecordComponent(result, "coverageMode")).isEqualTo("live_fallback");
        assertThat(readRecordComponent(result, "fallbackReason")).isEqualTo("outside_stream_coverage");
    }

    @Test
    void shouldReuseLiveFallbackSnapshotWithinTtlForRepeatedRequests() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto liveRow = sampleLiveRow("IBM", expiry, new BigDecimal("280.0000"));

        when(repository.findLatestCollectedAtByTicker("IBM")).thenReturn(Optional.empty());
        when(spotGexFetchService.getExpirationDates("IBM", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("IBM", List.of(expiry))).thenReturn(List.of(liveRow));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("ibm", null, null, null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("ibm", null, null, null);

        assertThat(first.data()).hasSize(1);
        assertThat(second.data()).hasSize(1);
        assertThat(second.collectedAt()).isEqualTo(first.collectedAt());
        verify(spotGexFetchService, times(1)).getExpirationDates("IBM", null);
        verify(spotGexFetchService, times(1)).fetchAllSpotExposurePages("IBM", List.of(expiry));
    }

    @Test
    void shouldReturnUnsubscribedStreamDegradationMetadataWithoutRestFallback() {
        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_live_gex", true, 1, List.of("QQQ")),
                Instant.parse("2026-06-11T14:32:56Z"),
                "QQQ",
                null,
                null,
                0
            )
        );

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("spy", null, null, null);

        assertThat(result.source()).isEqualTo("stream");
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_STREAM_TICKER_UNSUBSCRIBED");
        assertThat(result.streamConnected()).isTrue();
        assertThat(result.streamSubscribed()).isFalse();
        assertThat(result.streamStale()).isFalse();
        assertThat(result.latestBucketStart()).isNull();
        assertThat(result.latestBucketLagMillis()).isNull();
        verifyNoInteractions(spotGexFetchService);
        verifyNoInteractions(streamBucketRepository);
    }

    @Test
    void shouldQueryStreamHistoryWithoutLegacyWideHistoryDegradation() {
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            "SPY",
            LocalDate.of(2026, 6, 11),
            new BigDecimal("725.0000"),
            Instant.parse("2026-06-11T14:39:00Z"),
            "250.0000",
            "-75.0000"
        );

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(streamBucketRepository.findHistory("SPY", since, "5 minutes", null, null, null, null)).thenReturn(List.of(row));

        SpotGexQueryService.HistoryResult result =
            spotGexQueryService.getHistoryResult("spy", since, "5m", null, null);

        assertThat(result.degraded()).isFalse();
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().sourceTime()).isEqualTo(Instant.parse("2026-06-11T14:39:36Z"));
        verify(repository, never()).findHistory(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnDegradedStreamHistoryWhenBucketQueryTimesOut() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);

        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(streamBucketRepository.findHistory("SPY", since, "5 minutes", null, null, null, null))
            .thenThrow(new DataAccessResourceFailureException("canceling statement due to user request"));

        SpotGexQueryService.HistoryResult result =
            spotGexQueryService.getHistoryResult("spy", since, "5m", null, null);

        assertThat(result.data()).isEmpty();
        assertThat(result.degraded()).isTrue();
        assertThat(result.degradationCode()).isEqualTo("SPOT_GEX_STREAM_HISTORY_UNAVAILABLE");
        assertThat(result.degradationMessage()).contains("stream history is temporarily unavailable");
        verify(repository, never()).findHistory(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldResolveWeekendReplaySessionToLatestStoredNyMarketDate() {
        LocalDate requestedDate = LocalDate.of(2026, 6, 20);
        Instant saturdayOpen = Instant.parse("2026-06-20T13:30:00Z");
        Instant saturdayClose = Instant.parse("2026-06-20T20:15:00Z");
        Instant fridayOpen = Instant.parse("2026-06-19T13:30:00Z");
        Instant fridayClose = Instant.parse("2026-06-19T20:15:00Z");
        Instant firstBucket = Instant.parse("2026-06-19T13:30:00Z");
        Instant latestBucket = Instant.parse("2026-06-19T13:31:00Z");

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));
        when(streamBucketRepository.findReplaySessionBucketStarts("SPY", saturdayOpen, saturdayClose))
            .thenReturn(List.of());
        when(streamBucketRepository.findReplaySessionBucketStarts("SPY", fridayOpen, fridayClose))
            .thenReturn(List.of(firstBucket, latestBucket));

        SpotGexQueryService.ReplaySessionResult result =
            spotGexQueryService.getReplaySession("spy", requestedDate);

        assertThat(result.stored()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.requestedDate()).isEqualTo(requestedDate);
        assertThat(result.marketDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(result.timeZone()).isEqualTo("America/New_York");
        assertThat(result.openAt()).isEqualTo(fridayOpen);
        assertThat(result.closeAt()).isEqualTo(fridayClose);
        assertThat(result.availableBucketStarts()).containsExactly(firstBucket, latestBucket);
        assertThat(result.latestBucketStart()).isEqualTo(latestBucket);
        assertThat(result.hasGaps()).isFalse();
        assertThat(result.code()).isNull();
    }

    @Test
    void shouldDefaultReplaySessionToPreviousWeekdayBeforeNyMarketOpen() {
        ReflectionTestUtils.setField(
            spotGexQueryService,
            "clock",
            Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneOffset.UTC)
        );
        Instant mondayOpen = Instant.parse("2026-06-22T13:30:00Z");
        Instant mondayClose = Instant.parse("2026-06-22T20:15:00Z");
        Instant fridayOpen = Instant.parse("2026-06-19T13:30:00Z");
        Instant fridayClose = Instant.parse("2026-06-19T20:15:00Z");
        Instant firstBucket = Instant.parse("2026-06-19T13:30:00Z");
        Instant latestBucket = Instant.parse("2026-06-19T13:31:00Z");

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));
        when(streamBucketRepository.findReplaySessionBucketStarts("SPY", fridayOpen, fridayClose))
            .thenReturn(List.of(firstBucket, latestBucket));

        SpotGexQueryService.ReplaySessionResult result =
            spotGexQueryService.getReplaySession("spy", null);

        assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(result.marketDate()).isEqualTo(LocalDate.of(2026, 6, 19));
        assertThat(result.availableBucketStarts()).containsExactly(firstBucket, latestBucket);
        verify(streamBucketRepository, never()).findReplaySessionBucketStarts("SPY", mondayOpen, mondayClose);
    }

    @Test
    void shouldDefaultReplaySessionToSameWeekdayAfterNyMarketClose() {
        ReflectionTestUtils.setField(
            spotGexQueryService,
            "clock",
            Clock.fixed(Instant.parse("2026-06-18T22:00:00Z"), ZoneOffset.UTC)
        );
        Instant thursdayOpen = Instant.parse("2026-06-18T13:30:00Z");
        Instant thursdayClose = Instant.parse("2026-06-18T20:15:00Z");
        Instant firstBucket = Instant.parse("2026-06-18T13:30:00Z");
        Instant latestBucket = Instant.parse("2026-06-18T13:31:00Z");

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));
        when(streamBucketRepository.findReplaySessionBucketStarts("SPY", thursdayOpen, thursdayClose))
            .thenReturn(List.of(firstBucket, latestBucket));

        SpotGexQueryService.ReplaySessionResult result =
            spotGexQueryService.getReplaySession("spy", null);

        assertThat(result.requestedDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(result.marketDate()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(result.availableBucketStarts()).containsExactly(firstBucket, latestBucket);
    }

    @Test
    void shouldReturnReplayUnavailableForNonStoredTickerWithoutQueryingBuckets() {
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "QQQ"));

        SpotGexQueryService.ReplaySessionResult result =
            spotGexQueryService.getReplaySession("tsla", LocalDate.of(2026, 6, 19));

        assertThat(result.stored()).isFalse();
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_REPLAY_TICKER_UNSTORED");
        assertThat(result.message()).contains("TSLA");
        verify(streamBucketRepository, never()).findReplaySessionBucketStarts(any(), any(), any());
    }

    @Test
    void shouldUseStoredSpxwReplayRootForSpxReplaySession() {
        LocalDate marketDate = LocalDate.of(2026, 6, 18);
        Instant openAt = Instant.parse("2026-06-18T13:30:00Z");
        Instant closeAt = Instant.parse("2026-06-18T20:15:00Z");
        Instant firstBucket = Instant.parse("2026-06-18T13:30:00Z");
        Instant latestBucket = Instant.parse("2026-06-18T19:56:00Z");

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "SPXW"));
        when(streamBucketRepository.findReplaySessionBucketStarts("SPXW", openAt, closeAt))
            .thenReturn(List.of(firstBucket, latestBucket));

        SpotGexQueryService.ReplaySessionResult result =
            spotGexQueryService.getReplaySession("spx", marketDate);

        assertThat(result.ticker()).isEqualTo("SPX");
        assertThat(result.stored()).isTrue();
        assertThat(result.requestedDate()).isEqualTo(marketDate);
        assertThat(result.marketDate()).isEqualTo(marketDate);
        assertThat(result.openAt()).isEqualTo(openAt);
        assertThat(result.closeAt()).isEqualTo(closeAt);
        assertThat(result.availableBucketStarts()).containsExactly(firstBucket, latestBucket);
        verify(streamBucketRepository).findReplaySessionBucketStarts("SPXW", openAt, closeAt);
        verify(streamBucketRepository, never()).findReplaySessionBucketStarts(eq("SPX"), any(), any());
    }

    @Test
    void shouldReturnAsOfReplaySnapshotFromStoredSessionBucket() {
        Instant bucketStart = Instant.parse("2026-06-19T13:31:00Z");
        Instant sessionOpenAt = Instant.parse("2026-06-19T13:30:00Z");
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotGexExposureRepository.SpotGexHistoryProjection row =
            streamRow("SPY", expiry, new BigDecimal("725.0000"), bucketStart, "250.0000", "-75.0000");

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));
        when(streamBucketRepository.findReplaySnapshot(
            "SPY",
            bucketStart,
            sessionOpenAt,
            expiry,
            new BigDecimal("700"),
            new BigDecimal("750")
        )).thenReturn(List.of(row));

        SpotGexQueryService.ReplaySnapshotResult result = spotGexQueryService.getReplaySnapshot(
            "spy",
            bucketStart,
            expiry,
            new BigDecimal("700"),
            new BigDecimal("750")
        );

        assertThat(result.success()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.source()).isEqualTo("replay");
        assertThat(result.snapshotAt()).isEqualTo(bucketStart);
        assertThat(result.marketDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.availableExpiryCount()).isEqualTo(1);
        assertThat(result.availableStrikeCount()).isEqualTo(1);
        assertThat(result.data()).containsExactly(SpotGexSnapshotDto.from(row));
    }

    @Test
    void shouldUseStoredSpxwReplayRootForSpxReplaySnapshot() {
        Instant bucketStart = Instant.parse("2026-06-18T15:48:00Z");
        Instant sessionOpenAt = Instant.parse("2026-06-18T13:30:00Z");
        LocalDate expiry = LocalDate.of(2026, 6, 18);
        SpotGexExposureRepository.SpotGexHistoryProjection row =
            streamRow("SPXW", expiry, new BigDecimal("750.0000"), bucketStart, "186000000.0000", "-2000000.0000");

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "SPXW"));
        when(streamBucketRepository.findReplaySnapshot("SPXW", bucketStart, sessionOpenAt, null, null, null))
            .thenReturn(List.of(row));

        SpotGexQueryService.ReplaySnapshotResult result =
            spotGexQueryService.getReplaySnapshot("spx", bucketStart, null, null, null);

        assertThat(result.success()).isTrue();
        assertThat(result.ticker()).isEqualTo("SPX");
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().getFirst().ticker()).isEqualTo("SPX");
        assertThat(result.data().getFirst().expiry()).isEqualTo(expiry);
        verify(streamBucketRepository).findReplaySnapshot("SPXW", bucketStart, sessionOpenAt, null, null, null);
        verify(streamBucketRepository, never()).findReplaySnapshot(eq("SPX"), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnMissingBucketReplaySnapshotWhenAsOfSessionHasNoRows() {
        Instant bucketStart = Instant.parse("2026-06-19T13:31:00Z");
        Instant sessionOpenAt = Instant.parse("2026-06-19T13:30:00Z");

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));
        when(streamBucketRepository.findReplaySnapshot("SPY", bucketStart, sessionOpenAt, null, null, null))
            .thenReturn(List.of());

        SpotGexQueryService.ReplaySnapshotResult result =
            spotGexQueryService.getReplaySnapshot("spy", bucketStart, null, null, null);

        assertThat(result.success()).isFalse();
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_REPLAY_BUCKET_MISSING");
        assertThat(result.message()).contains("SPY");
        assertThat(result.data()).isEmpty();
    }

    @Test
    void shouldReturnDegradedLiveFallbackWhenNoOptionChainExpirationsExist() {
        when(repository.findLatestCollectedAtByTicker("OXY")).thenReturn(Optional.empty());
        when(spotGexFetchService.getExpirationDates("OXY", null)).thenReturn(List.of());

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("oxy", null, null, null);

        assertThat(result.collectedAt()).isNull();
        assertThat(result.data()).isEmpty();
        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_OPTION_CHAIN_EMPTY");
        assertThat(result.message()).contains("No option chain expirations");
        verify(spotGexFetchService, never()).fetchAllSpotExposurePages(any(), any());
    }

    @Test
    void shouldReturnDegradedLiveFallbackWhenSpotExposureRowsAreEmpty() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);

        when(repository.findLatestCollectedAtByTicker("OXY")).thenReturn(Optional.empty());
        when(spotGexFetchService.getExpirationDates("OXY", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("OXY", List.of(expiry))).thenReturn(List.of());

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("oxy", null, null, null);

        assertThat(result.collectedAt()).isNull();
        assertThat(result.data()).isEmpty();
        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_EXPOSURE_ROWS_EMPTY");
        assertThat(result.message()).contains("No live Spot GEX exposure rows");
    }

    @Test
    void shouldReturnDegradedLiveFallbackWhenSpotExposureRowsAreUnusable() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto missingStrikeRow = sampleLiveRow("OXY", expiry, null);

        when(repository.findLatestCollectedAtByTicker("OXY")).thenReturn(Optional.empty());
        when(spotGexFetchService.getExpirationDates("OXY", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("OXY", List.of(expiry)))
            .thenReturn(List.of(missingStrikeRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("oxy", null, null, null);

        assertThat(result.collectedAt()).isNull();
        assertThat(result.data()).isEmpty();
        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_EXPOSURE_ROWS_INVALID");
        assertThat(result.message()).contains("usable expiry and strike values");
    }

    @Test
    void shouldReturnNonDegradedLiveFallbackWhenValidRowsDoNotMatchFilters() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto belowRangeRow = sampleLiveRow("OXY", expiry, new BigDecimal("80.0000"));

        when(repository.findLatestCollectedAtByTicker("OXY")).thenReturn(Optional.empty());
        when(spotGexFetchService.fetchAllSpotExposurePages("OXY", List.of(expiry)))
            .thenReturn(List.of(belowRangeRow));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("oxy", expiry, new BigDecimal("90"), null);

        assertThat(result.collectedAt()).isNotNull();
        assertThat(result.data()).isEmpty();
        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isFalse();
        assertThat(result.code()).isNull();
        verify(spotGexFetchService, never()).getExpirationDates(any(), any());
    }

    @Test
    void shouldCacheNonDegradedEmptyLiveFallbackWithinTtl() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotExposureByStrikeDto belowRangeRow = sampleLiveRow("OXY", expiry, new BigDecimal("80.0000"));

        when(repository.findLatestCollectedAtByTicker("OXY")).thenReturn(Optional.empty());
        when(spotGexFetchService.fetchAllSpotExposurePages("OXY", List.of(expiry)))
            .thenReturn(List.of(belowRangeRow));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("oxy", expiry, new BigDecimal("90"), null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("oxy", expiry, new BigDecimal("90"), null);

        assertThat(first.degraded()).isFalse();
        assertThat(second.degraded()).isFalse();
        assertThat(first.data()).isEmpty();
        assertThat(second.data()).isEmpty();
        verify(spotGexFetchService, times(1)).fetchAllSpotExposurePages("OXY", List.of(expiry));
    }

    @Test
    void shouldReturnDegradedLiveFallbackWhenProviderRequestFails() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);

        when(repository.findLatestCollectedAtByTicker("CVX")).thenReturn(Optional.empty());
        when(spotGexFetchService.getExpirationDates("CVX", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("CVX", List.of(expiry)))
            .thenThrow(new IllegalStateException("upstream timeout"));

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot("cvx", null, null, null);

        assertThat(result.collectedAt()).isNull();
        assertThat(result.data()).isEmpty();
        assertThat(result.source()).isEqualTo("live");
        assertThat(result.degraded()).isTrue();
        assertThat(result.code()).isEqualTo("SPOT_GEX_LIVE_FALLBACK_FAILED");
        assertThat(result.message()).contains("Unable to load live Spot GEX data");
    }

    @Test
    void shouldNotCacheLiveFallbackProviderFailures() {
        LocalDate expiry = LocalDate.of(2026, 6, 19);

        when(repository.findLatestCollectedAtByTicker("CVX")).thenReturn(Optional.empty());
        when(spotGexFetchService.getExpirationDates("CVX", null)).thenReturn(List.of(expiry));
        when(spotGexFetchService.fetchAllSpotExposurePages("CVX", List.of(expiry)))
            .thenThrow(new IllegalStateException("upstream timeout"));

        SpotGexQueryService.LatestSnapshotResult first =
            spotGexQueryService.getLatestSnapshot("cvx", null, null, null);
        SpotGexQueryService.LatestSnapshotResult second =
            spotGexQueryService.getLatestSnapshot("cvx", null, null, null);

        assertThat(first.code()).isEqualTo("SPOT_GEX_LIVE_FALLBACK_FAILED");
        assertThat(second.code()).isEqualTo("SPOT_GEX_LIVE_FALLBACK_FAILED");
        verify(spotGexFetchService, times(2)).getExpirationDates("CVX", null);
        verify(spotGexFetchService, times(2)).fetchAllSpotExposurePages("CVX", List.of(expiry));
    }

    @Test
    void shouldCombinePersistedAndRuntimeCollectionStatusInConfiguredOrder() {
        SpotGexExposureRepository.SpotGexStatusProjection persistedSpy = mock(SpotGexExposureRepository.SpotGexStatusProjection.class);
        when(persistedSpy.getTicker()).thenReturn("SPY");
        when(persistedSpy.getLastCollectedAt()).thenReturn(Instant.parse("2026-04-06T14:31:00Z"));
        when(persistedSpy.getRowsToday()).thenReturn(42L);
        when(persistedSpy.getCollectionCycles()).thenReturn(3L);

        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("QQQ", "SPY"));
        when(repository.findCollectionStatus(List.of("QQQ", "SPY"))).thenReturn(List.of(persistedSpy));
        when(spotGexCollectionService.getRuntimeStatusSnapshot()).thenReturn(Map.of(
            "QQQ",
            new SpotGexCollectionService.TickerRuntimeStatus(
                Instant.parse("2026-04-06T14:35:00Z"),
                null,
                "boom",
                2,
                0,
                0,
                0,
                false
            ),
            "SPY",
            new SpotGexCollectionService.TickerRuntimeStatus(
                Instant.parse("2026-04-06T14:31:00Z"),
                Instant.parse("2026-04-06T14:31:00Z"),
                null,
                0,
                10,
                10,
                0,
                true
            )
        ));

        List<SpotGexCollectionStatusDto> statuses = spotGexQueryService.getCollectionStatus();

        assertThat(statuses).hasSize(2);
        assertThat(statuses.get(0).ticker()).isEqualTo("QQQ");
        assertThat(statuses.get(0).lastError()).isEqualTo("boom");
        assertThat(statuses.get(0).healthy()).isFalse();
        assertThat(statuses.get(1).ticker()).isEqualTo("SPY");
        assertThat(statuses.get(1).rowsToday()).isEqualTo(42);
        assertThat(statuses.get(1).collectionCycles()).isEqualTo(3);
        assertThat(statuses.get(1).healthy()).isTrue();
        verify(repository).findCollectionStatus(List.of("QQQ", "SPY"));
    }

    @Test
    void shouldIncludeStreamPersistenceStatusInCollectionStatusWhenStreamMode() {
        Instant latestBucket = Instant.now().minusMillis(30_000L);
        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("crm", "TSLA"));
        when(repository.findCollectionStatus(List.of("CRM", "TSLA"))).thenReturn(List.of());
        when(spotGexCollectionService.getRuntimeStatusSnapshot()).thenReturn(Map.of());
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_ws", true, 2, List.of("CRM", "TSLA")),
                Instant.parse("2026-06-24T19:59:30Z"),
                "CRM",
                Instant.parse("2026-06-24T20:00:00Z"),
                latestBucket,
                0
            )
        );
        when(streamBucketRepository.findStatusByTicker(List.of("CRM", "TSLA"))).thenReturn(List.of(
            new SpotGexStreamBucketRepository.StreamBucketStatusProjection("CRM", latestBucket, 0L, 0L, 919L),
            new SpotGexStreamBucketRepository.StreamBucketStatusProjection("TSLA", null, 0L, 0L, 0L)
        ));

        List<SpotGexCollectionStatusDto> statuses = spotGexQueryService.getCollectionStatus();

        assertThat(statuses).hasSize(2);
        SpotGexCollectionStatusDto crm = statuses.get(0);
        assertThat(crm.ticker()).isEqualTo("CRM");
        assertThat(crm.sourceMode()).isEqualTo("stream");
        assertThat(crm.streamSubscribed()).isTrue();
        assertThat(crm.latestStreamBucketStart()).isEqualTo(latestBucket);
        assertThat(crm.streamRowsToday()).isZero();
        assertThat(crm.streamRowsLatestSession()).isZero();
        assertThat(crm.streamCurrentRows()).isEqualTo(919L);
        assertThat(crm.streamStale()).isFalse();
        assertThat(crm.healthy()).isTrue();

        SpotGexCollectionStatusDto tsla = statuses.get(1);
        assertThat(tsla.ticker()).isEqualTo("TSLA");
        assertThat(tsla.streamSubscribed()).isTrue();
        assertThat(tsla.latestStreamBucketStart()).isNull();
        assertThat(tsla.streamRowsLatestSession()).isZero();
        assertThat(tsla.streamCurrentRows()).isZero();
        assertThat(tsla.streamStale()).isTrue();
        assertThat(tsla.healthy()).isFalse();
    }

    @Test
    void shouldReportRestOnlyCollectionStatusFromCurrentRowsWithoutStreamHealth() {
        Instant latestBucket = Instant.now().minusMillis(30_000L);
        Instant successfulCollection = Instant.parse("2026-06-25T14:45:00Z");
        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPXW"));
        when(repository.findCollectionStatus(List.of("SPXW"))).thenReturn(List.of());
        when(spotGexCollectionService.getRuntimeStatusSnapshot()).thenReturn(Map.of(
            "SPX",
            new SpotGexCollectionService.TickerRuntimeStatus(
                Instant.parse("2026-06-25T14:45:01Z"),
                successfulCollection,
                null,
                0,
                2,
                2,
                0,
                true
            )
        ));
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_ws", true, 1, List.of("SPY")),
                Instant.parse("2026-06-25T14:32:56Z"),
                "SPY",
                Instant.parse("2026-06-25T14:33:00Z"),
                latestBucket,
                0
            )
        );
        when(streamBucketRepository.findStatusByTicker(List.of("SPXW", "SPX"))).thenReturn(List.of(
            new SpotGexStreamBucketRepository.StreamBucketStatusProjection("SPXW", null, 0L, 0L, 0L),
            new SpotGexStreamBucketRepository.StreamBucketStatusProjection("SPX", latestBucket, 0L, 0L, 430L)
        ));

        List<SpotGexCollectionStatusDto> statuses = spotGexQueryService.getCollectionStatus();

        assertThat(statuses).hasSize(1);
        SpotGexCollectionStatusDto spxw = statuses.getFirst();
        assertThat(spxw.ticker()).isEqualTo("SPXW");
        assertThat(spxw.sourceMode()).isEqualTo("rest_only");
        assertThat(spxw.streamSubscribed()).isFalse();
        assertThat(spxw.streamStale()).isFalse();
        assertThat(spxw.streamCurrentRows()).isEqualTo(430L);
        assertThat(spxw.lastSuccessfulCollectionAt()).isEqualTo(successfulCollection);
        assertThat(spxw.healthy()).isTrue();
    }

    @Test
    void shouldSkipCollectionStatusQueryWhenNoTickersAreConfigured() {
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of());

        List<SpotGexCollectionStatusDto> statuses = spotGexQueryService.getCollectionStatus();

        assertThat(statuses).isEmpty();
        verify(repository, never()).findCollectionStatus(any());
        verify(spotGexCollectionService, never()).getRuntimeStatusSnapshot();
    }

    @Test
    void shouldReturnStreamStatusWithLagAndStaleTickerMetadata() {
        Instant freshBucket = Instant.now().minusMillis(30_000L);
        Instant staleBucket = Instant.now().minusMillis(180_000L);
        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("spy", "QQQ", "IWM"));
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_ws", true, 2, List.of("SPY", "QQQ")),
                Instant.parse("2026-06-11T14:32:56Z"),
                "SPY",
                Instant.parse("2026-06-11T14:33:00Z"),
                freshBucket,
                0
            )
        );
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(freshBucket));
        when(streamBucketRepository.findLatestBucketStartByTicker("QQQ")).thenReturn(Optional.of(staleBucket));
        when(streamBucketRepository.findLatestBucketStartByTicker("IWM")).thenReturn(Optional.empty());

        SpotGexStreamStatusDto status = spotGexQueryService.getStreamStatus();

        assertThat(status.sourceMode()).isEqualTo("stream");
        assertThat(status.nodeId()).isEqualTo("backend-a-prod");
        assertThat(status.ownerNodeId()).isEqualTo("backend-a-prod");
        assertThat(status.owner()).isTrue();
        assertThat(status.transport()).isEqualTo("options_feed_ws");
        assertThat(status.active()).isTrue();
        assertThat(status.connected()).isTrue();
        assertThat(status.activeTickerCount()).isEqualTo(2);
        assertThat(status.subscribedTickers()).containsExactly("SPY", "QQQ");
        assertThat(status.stale()).isTrue();
        assertThat(status.staleTickerCount()).isEqualTo(2);
        assertThat(status.tickers()).extracting(SpotGexStreamStatusDto.TickerStatus::ticker)
            .containsExactly("SPY", "QQQ", "IWM");
        assertThat(status.tickers().get(0).subscribed()).isTrue();
        assertThat(status.tickers().get(0).stale()).isFalse();
        assertThat(status.tickers().get(1).stale()).isTrue();
        assertThat(status.tickers().get(2).latestBucketStart()).isNull();
        assertThat(status.tickers().get(2).stale()).isTrue();
    }

    @Test
    void shouldExcludeRestOnlyTickersFromStreamStatusDiagnostics() {
        Instant freshBucket = Instant.now().minusMillis(30_000L);
        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(properties.getStreamStaleThresholdMs()).thenReturn(120_000L);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("spy", "SPXW", "VIXW"));
        when(streamIngestionService.getRuntimeStatusSnapshot()).thenReturn(
            new SpotGexStreamIngestionService.StreamRuntimeStatus(
                true,
                true,
                true,
                "backend-a-prod",
                "backend-a-prod",
                new SpotGexStreamHealth("options_feed_ws", true, 1, List.of("SPY")),
                Instant.parse("2026-06-25T14:32:56Z"),
                "SPY",
                Instant.parse("2026-06-25T14:33:00Z"),
                freshBucket,
                0
            )
        );
        when(streamBucketRepository.findLatestBucketStartByTicker("SPY")).thenReturn(Optional.of(freshBucket));

        SpotGexStreamStatusDto status = spotGexQueryService.getStreamStatus();

        assertThat(status.stale()).isFalse();
        assertThat(status.staleTickerCount()).isZero();
        assertThat(status.tickers()).extracting(SpotGexStreamStatusDto.TickerStatus::ticker)
            .containsExactly("SPY");
        verify(streamBucketRepository, never()).findLatestBucketStartByTicker("SPXW");
        verify(streamBucketRepository, never()).findLatestBucketStartByTicker("VIXW");
    }

    @Test
    void shouldRejectUnsupportedHistoryBucket() {
        assertThatThrownBy(() ->
            spotGexQueryService.getHistory("SPY", Instant.now(), "30m", null, null)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported bucket");
    }

    @Test
    void shouldClampFiveMinuteHistoryLookbackToFifteenDays() {
        Instant requestedSince = Instant.now().minus(30, ChronoUnit.DAYS);
        LocalDate expiry = LocalDate.of(2026, 6, 5);

        when(repository.findHistory(eq("SPY"), any(Instant.class), eq("5 minutes"), eq(expiry), eq(null), eq(null), eq(null)))
            .thenReturn(List.of());

        spotGexQueryService.getHistory("spy", requestedSince, "5m", expiry, null);

        org.mockito.ArgumentCaptor<Instant> sinceCaptor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).findHistory(eq("SPY"), sinceCaptor.capture(), eq("5 minutes"), eq(expiry), eq(null), eq(null), eq(null));
        assertThat(sinceCaptor.getValue())
            .isAfter(Instant.now().minus(15, ChronoUnit.DAYS).minus(5, ChronoUnit.SECONDS))
            .isBefore(Instant.now().minus(15, ChronoUnit.DAYS).plus(5, ChronoUnit.SECONDS));
    }

    @Test
    void shouldDegradeWideUnfilteredFiveMinuteHistoryWithoutQueryingRepository() {
        Instant requestedSince = Instant.now().minus(15, ChronoUnit.DAYS);

        SpotGexQueryService.HistoryResult result =
            spotGexQueryService.getHistoryResult("spy", requestedSince, "5m", null, null);

        assertThat(result.data()).isEmpty();
        assertThat(result.degraded()).isTrue();
        assertThat(result.degradationCode()).isEqualTo("SPOT_GEX_HISTORY_WINDOW_TOO_WIDE");
        verify(repository, never()).findHistory(any(), any(), any(), any(), any(), any(), any());
    }

    private static SpotExposureByStrikeDto sampleLiveRow(String ticker, LocalDate expiry, BigDecimal strike) {
        return new SpotExposureByStrikeDto(
            ticker,
            decimal("1.1000"),
            decimal("2.1000"),
            decimal("10.1000"),
            decimal("4.1000"),
            LocalDate.of(2026, 6, 8),
            decimal("5.1000"),
            decimal("6.1000"),
            decimal("7.1000"),
            decimal("8.1000"),
            decimal("9.1000"),
            decimal("10.1000"),
            decimal("11.1000"),
            decimal("12.1000"),
            decimal("13.1000"),
            OffsetDateTime.parse("2026-06-08T14:30:00Z"),
            decimal("14.1000"),
            decimal("15.1000"),
            decimal("16.1000"),
            decimal("17.1000"),
            decimal("18.1000"),
            decimal("19.1000"),
            decimal("20.1000"),
            decimal("21.1000"),
            decimal("22.1000"),
            decimal("23.1000"),
            decimal("24.1000"),
            strike,
            decimal("25.1000"),
            decimal("26.1000"),
            decimal("27.1000"),
            decimal("28.1000"),
            decimal("29.1000"),
            decimal("30.1000"),
            decimal("31.1000"),
            decimal("32.1000"),
            decimal("33.1000"),
            expiry
        );
    }

    private void enableLatestSnapshotCache() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(new ConcurrentMapCache("spotGexLatestSnapshotCache")));
        cacheManager.afterPropertiesSet();
        ReflectionTestUtils.setField(spotGexQueryService, "spotGexLatestSnapshotCacheManager", cacheManager);
    }

    private static SpotGexExposureRepository.SpotGexHistoryProjection streamRow(
        String ticker,
        LocalDate expiry,
        BigDecimal strike,
        Instant snapshotAt,
        String callGammaOi,
        String putGammaOi
    ) {
        SpotGexExposureRepository.SpotGexHistoryProjection row =
            mock(SpotGexExposureRepository.SpotGexHistoryProjection.class);
        when(row.getTicker()).thenReturn(ticker);
        when(row.getExpiry()).thenReturn(expiry);
        when(row.getStrike()).thenReturn(strike);
        when(row.getSnapshotAt()).thenReturn(snapshotAt);
        when(row.getPrice()).thenReturn(new BigDecimal("725.6750"));
        when(row.getMarketDate()).thenReturn(LocalDate.of(2026, 6, 11));
        when(row.getSourceTime()).thenReturn(Instant.parse("2026-06-11T14:39:36Z"));
        when(row.getCallGammaOi()).thenReturn(new BigDecimal(callGammaOi));
        when(row.getPutGammaOi()).thenReturn(new BigDecimal(putGammaOi));
        return row;
    }

    private static SpotGexExposureRepository.SpotGexHistoryProjection streamRowWithDelta(
        String ticker,
        LocalDate expiry,
        BigDecimal strike,
        Instant snapshotAt
    ) {
        SpotGexExposureRepository.SpotGexHistoryProjection row = streamRow(
            ticker,
            expiry,
            strike,
            snapshotAt,
            "250.0000",
            "-75.0000"
        );
        when(row.getCallDeltaOi()).thenReturn(new BigDecimal("101.0000"));
        when(row.getPutDeltaOi()).thenReturn(new BigDecimal("-99.0000"));
        when(row.getCallVannaOi()).thenReturn(new BigDecimal("50.0000"));
        when(row.getPutVannaOi()).thenReturn(new BigDecimal("-40.0000"));
        when(row.getCallCharmOi()).thenReturn(new BigDecimal("30.0000"));
        when(row.getPutCharmOi()).thenReturn(new BigDecimal("-20.0000"));
        when(row.getCallDeltaVol()).thenReturn(new BigDecimal("11.0000"));
        when(row.getPutDeltaVol()).thenReturn(new BigDecimal("-9.0000"));
        when(row.getCallGammaVol()).thenReturn(new BigDecimal("21.0000"));
        when(row.getPutGammaVol()).thenReturn(new BigDecimal("-19.0000"));
        when(row.getCallVannaVol()).thenReturn(new BigDecimal("31.0000"));
        when(row.getPutVannaVol()).thenReturn(new BigDecimal("-29.0000"));
        when(row.getCallCharmVol()).thenReturn(new BigDecimal("41.0000"));
        when(row.getPutCharmVol()).thenReturn(new BigDecimal("-39.0000"));
        when(row.getCallDeltaBid()).thenReturn(new BigDecimal("8.0000"));
        when(row.getCallDeltaAsk()).thenReturn(new BigDecimal("12.0000"));
        when(row.getCallGammaBid()).thenReturn(new BigDecimal("18.0000"));
        when(row.getCallGammaAsk()).thenReturn(new BigDecimal("22.0000"));
        when(row.getCallVannaBid()).thenReturn(new BigDecimal("28.0000"));
        when(row.getCallVannaAsk()).thenReturn(new BigDecimal("32.0000"));
        when(row.getCallCharmBid()).thenReturn(new BigDecimal("38.0000"));
        when(row.getCallCharmAsk()).thenReturn(new BigDecimal("42.0000"));
        when(row.getPutDeltaBid()).thenReturn(new BigDecimal("-7.0000"));
        when(row.getPutDeltaAsk()).thenReturn(new BigDecimal("-13.0000"));
        when(row.getPutGammaBid()).thenReturn(new BigDecimal("-17.0000"));
        when(row.getPutGammaAsk()).thenReturn(new BigDecimal("-23.0000"));
        when(row.getPutVannaBid()).thenReturn(new BigDecimal("-27.0000"));
        when(row.getPutVannaAsk()).thenReturn(new BigDecimal("-33.0000"));
        when(row.getPutCharmBid()).thenReturn(new BigDecimal("-37.0000"));
        when(row.getPutCharmAsk()).thenReturn(new BigDecimal("-43.0000"));
        return row;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static List<String> recordComponentNames() {
        return Arrays.stream(SpotGexQueryService.LatestSnapshotResult.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();
    }

    private static Object readRecordComponent(SpotGexQueryService.LatestSnapshotResult result, String name) {
        try {
            return SpotGexQueryService.LatestSnapshotResult.class.getDeclaredMethod(name).invoke(result);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing LatestSnapshotResult component " + name, exception);
        }
    }
}
