package com.marketmono.service.spotgex;

import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.service.CollectionConfigService;
import com.marketmono.service.NodeIdentityService;
import com.marketmono.service.SpotGexTickerSourcePolicy;
import com.marketmono.service.StrataTickerFamilyResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexStreamIngestionServiceTest {

    @Mock
    private SpotGexStreamAdapter streamAdapter;

    @Mock
    private SpotGexOneMinuteBucketAggregator aggregator;

    @Mock
    private SpotGexStreamBucketPersistence bucketPersistence;

    @Mock
    private CollectionConfigService collectionConfigService;

    @Mock
    private NodeIdentityService nodeIdentityService;

    private SpotGexCollectionProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private SpotGexStreamIngestionService service;
    private SpotGexTickerSourcePolicy tickerSourcePolicy;

    @BeforeEach
    void setUp() {
        properties = new SpotGexCollectionProperties();
        meterRegistry = new SimpleMeterRegistry();
        tickerSourcePolicy = new SpotGexTickerSourcePolicy(new StrataTickerFamilyResolver());
        service = new SpotGexStreamIngestionService(
            properties,
            collectionConfigService,
            streamAdapter,
            aggregator,
            bucketPersistence,
            nodeIdentityService,
            tickerSourcePolicy,
            meterRegistry
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRegisterHandlerAndSubscribeConfiguredTickersInStreamMode() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of(" spy ", "QQQ"));

        service.start();

        ArgumentCaptor<Consumer<SpotGexStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(streamAdapter).onEvent(handlerCaptor.capture());
        verify(streamAdapter).subscribe("SPY");
        verify(streamAdapter).subscribe("QQQ");

        SpotGexStreamEvent event = sampleEvent("SPY");
        handlerCaptor.getValue().accept(event);
        service.flushClosedBuckets(1_781_198_400_000L);
        verify(aggregator).accept(event);
    }

    @Test
    void shouldStartAfterApplicationReadySoKafkaListenerContainersAreRegistered() throws NoSuchMethodException {
        var startMethod = SpotGexStreamIngestionService.class.getDeclaredMethod("start");

        assertThat(startMethod.getAnnotation(PostConstruct.class)).isNull();
        assertThat(startMethod.getAnnotation(EventListener.class).value()).containsExactly(ApplicationReadyEvent.class);
    }

    @Test
    void shouldNotSubscribeOrFlushWhenLegacyModeIsActive() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.LEGACY);

        service.start();
        service.reconcileSubscriptions();
        service.flushClosedBuckets(1_781_198_400_000L);

        verify(streamAdapter, never()).onEvent(any());
        verify(streamAdapter, never()).subscribe(any());
        verify(aggregator, never()).drainReadyBuckets(any(Long.class));
        verify(bucketPersistence, never()).persistBuckets(any());
    }

    @Test
    void shouldStandByWhenActiveActiveOwnerDoesNotMatchCurrentNode() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        properties.setStreamOwnerNodeId("backend-b-prod");
        when(nodeIdentityService.getNodeId()).thenReturn("backend-a-prod");

        service.start();
        service.reconcileSubscriptions();
        service.flushClosedBuckets(1_781_198_400_000L);

        verify(streamAdapter, never()).onEvent(any());
        verify(streamAdapter, never()).subscribe(any());
        verify(aggregator, never()).drainReadyBuckets(any(Long.class));
        verify(bucketPersistence, never()).persistBuckets(any());
    }

    @Test
    void shouldUnsubscribeRemovedTickersDuringReconciliation() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        when(collectionConfigService.getSpotGexTickers())
            .thenReturn(List.of("SPY", "QQQ"))
            .thenReturn(List.of("QQQ", "IWM"));

        service.reconcileSubscriptions();
        service.reconcileSubscriptions();

        verify(streamAdapter).subscribe("SPY");
        verify(streamAdapter).subscribe("QQQ");
        verify(streamAdapter).subscribe("IWM");
        verify(streamAdapter).unsubscribe("SPY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeRestOnlyTickersFromKafkaSubscriptionsAndEvents() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "SPX", "SPXW", "VIX", "VIXW"));

        service.start();

        ArgumentCaptor<Consumer<SpotGexStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(streamAdapter).onEvent(handlerCaptor.capture());
        verify(streamAdapter).subscribe("SPY");
        verify(streamAdapter, never()).subscribe("SPX");
        verify(streamAdapter, never()).subscribe("SPXW");
        verify(streamAdapter, never()).subscribe("VIX");
        verify(streamAdapter, never()).subscribe("VIXW");

        handlerCaptor.getValue().accept(sampleEvent("SPX"));
        service.flushClosedBuckets(1_781_198_400_000L);

        verify(aggregator, never()).accept(any());
    }

    @Test
    void shouldFlushReadyBucketsToPersistence() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        SpotGexStreamBucket bucket = SpotGexStreamBucket.fromLatestEvent(sampleEvent("SPY"));
        when(aggregator.drainReadyBuckets(1_781_198_400_000L)).thenReturn(List.of(bucket));

        service.flushClosedBuckets(1_781_198_400_000L);

        verify(bucketPersistence).persistBuckets(List.of(bucket));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeRuntimeStatusForStreamVisibility() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));
        when(streamAdapter.health()).thenReturn(new SpotGexStreamHealth(
            "options_feed_ws",
            true,
            1,
            List.of("SPY")
        ));
        SpotGexStreamBucket bucket = SpotGexStreamBucket.fromLatestEvent(sampleEvent("SPY"));
        when(aggregator.drainReadyBuckets(1_781_198_400_000L)).thenReturn(List.of(bucket));

        service.start();

        ArgumentCaptor<Consumer<SpotGexStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(streamAdapter).onEvent(handlerCaptor.capture());
        handlerCaptor.getValue().accept(sampleEvent("SPY"));
        service.flushClosedBuckets(1_781_198_400_000L);

        SpotGexStreamIngestionService.StreamRuntimeStatus status = service.getRuntimeStatusSnapshot();

        assertThat(status.active()).isTrue();
        assertThat(status.owner()).isTrue();
        assertThat(status.health().connected()).isTrue();
        assertThat(status.lastEventAt()).isEqualTo(Instant.ofEpochMilli(1_781_198_376_000L));
        assertThat(status.lastEventTicker()).isEqualTo("SPY");
        assertThat(status.lastPersistedBucketStart()).isEqualTo(Instant.ofEpochMilli(bucket.bucketStartMs()));
        assertThat(status.lastFlushAt()).isNotNull();
        assertThat(meterRegistry.get("spot_gex.stream.active").gauge().value()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("spot_gex.stream.connected").gauge().value()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("spot_gex.stream.active_tickers").gauge().value()).isEqualTo(1.0d);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBoundPendingHandoffAndDropOldestOverflowWithMetrics() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        properties.setStreamMaxPendingEvents(2);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));

        service.start();

        ArgumentCaptor<Consumer<SpotGexStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(streamAdapter).onEvent(handlerCaptor.capture());
        SpotGexStreamEvent oldest = sampleEvent("SPY", LocalDate.of(2026, 6, 11), new BigDecimal("725"), 1_781_198_376_000L);
        SpotGexStreamEvent middle = sampleEvent("SPY", LocalDate.of(2026, 6, 11), new BigDecimal("726"), 1_781_198_376_000L);
        SpotGexStreamEvent newest = sampleEvent("SPY", LocalDate.of(2026, 6, 11), new BigDecimal("727"), 1_781_198_376_000L);

        handlerCaptor.getValue().accept(oldest);
        handlerCaptor.getValue().accept(middle);
        handlerCaptor.getValue().accept(newest);

        assertThat(service.getRuntimeStatusSnapshot().pendingEventCount()).isEqualTo(2);
        assertThat(meterRegistry.get("spot_gex.stream.events.dropped").counter().count()).isEqualTo(1.0d);

        service.flushClosedBuckets(1_781_198_440_000L);

        verify(aggregator, never()).accept(oldest);
        verify(aggregator).accept(middle);
        verify(aggregator).accept(newest);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepNewestEventForSameBucketKeyBeforeAggregation() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        properties.setStreamMaxPendingEvents(4);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));

        service.start();

        ArgumentCaptor<Consumer<SpotGexStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(streamAdapter).onEvent(handlerCaptor.capture());
        SpotGexStreamEvent older = sampleEvent("SPY", LocalDate.of(2026, 6, 11), new BigDecimal("725"), 1_781_198_376_000L);
        SpotGexStreamEvent newer = sampleEvent("SPY", LocalDate.of(2026, 6, 11), new BigDecimal("725"), 1_781_198_399_000L);

        handlerCaptor.getValue().accept(older);
        handlerCaptor.getValue().accept(newer);

        assertThat(service.getRuntimeStatusSnapshot().pendingEventCount()).isEqualTo(1);
        assertThat(meterRegistry.get("spot_gex.stream.events.coalesced").counter().count()).isEqualTo(1.0d);

        service.flushClosedBuckets(1_781_198_440_000L);

        verify(aggregator, never()).accept(older);
        verify(aggregator).accept(newer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectInvalidCanonicalEventsBeforeAggregation() {
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY"));

        service.start();

        ArgumentCaptor<Consumer<SpotGexStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(streamAdapter).onEvent(handlerCaptor.capture());
        handlerCaptor.getValue().accept(SpotGexStreamEvent.builder()
            .ticker("SPY")
            .timestampMs(1_781_198_376_000L)
            .ingestedAtMs(1_781_198_376_100L)
            .underlyingPrice(new BigDecimal("725.6750"))
            .source("options_feed_live_gex")
            .rawChannelName("live-gex:gex-strike-expiry-v2")
            .build());

        verify(aggregator, never()).accept(any());
    }

    private static SpotGexStreamEvent sampleEvent(String ticker) {
        return sampleEvent(ticker, LocalDate.of(2026, 6, 11), new BigDecimal("725"), 1_781_198_376_000L);
    }

    private static SpotGexStreamEvent sampleEvent(String ticker, LocalDate expiry, BigDecimal strike, long timestampMs) {
        return SpotGexStreamEvent.builder()
            .ticker(ticker)
            .expiry(expiry)
            .strikePrice(strike)
            .strikePriceCents(strike.movePointRight(2).intValueExact())
            .timestampMs(timestampMs)
            .bucketStartMs(timestampMs - (timestampMs % 60_000L))
            .ingestedAtMs(timestampMs + 100L)
            .underlyingPrice(new BigDecimal("725.6750"))
            .callGammaOi(new BigDecimal("250.0000"))
            .putGammaOi(new BigDecimal("-75.0000"))
            .source("options_feed_ws")
            .rawChannelName("gex_strike_expiry:" + ticker)
            .build();
    }
}
