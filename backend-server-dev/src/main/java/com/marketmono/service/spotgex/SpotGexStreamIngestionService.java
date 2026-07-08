package com.marketmono.service.spotgex;

import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.service.CollectionConfigService;
import com.marketmono.service.NodeIdentityService;
import com.marketmono.service.SpotGexTickerSourcePolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class SpotGexStreamIngestionService {

    private final SpotGexCollectionProperties properties;
    private final CollectionConfigService collectionConfigService;
    private final SpotGexStreamAdapter streamAdapter;
    private final SpotGexOneMinuteBucketAggregator aggregator;
    private final SpotGexStreamBucketPersistence bucketPersistence;
    private final NodeIdentityService nodeIdentityService;
    private final SpotGexTickerSourcePolicy tickerSourcePolicy;
    private final Counter acceptedEventCounter;
    private final Counter invalidEventCounter;
    private final Counter coalescedEventCounter;
    private final Counter droppedEventCounter;
    private final AtomicInteger pendingEventGauge = new AtomicInteger();
    private final Object pendingEventsLock = new Object();
    private final LinkedHashMap<String, SpotGexStreamEvent> pendingEvents = new LinkedHashMap<>();
    private final Set<String> subscribedTickers = new ConcurrentSkipListSet<>();
    private volatile Instant lastEventAt;
    private volatile String lastEventTicker;
    private volatile Instant lastFlushAt;
    private volatile Instant lastPersistedBucketStart;

    public SpotGexStreamIngestionService(
            SpotGexCollectionProperties properties,
            CollectionConfigService collectionConfigService,
            SpotGexStreamAdapter streamAdapter,
            SpotGexOneMinuteBucketAggregator aggregator,
            SpotGexStreamBucketPersistence bucketPersistence,
            NodeIdentityService nodeIdentityService,
            SpotGexTickerSourcePolicy tickerSourcePolicy,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.collectionConfigService = collectionConfigService;
        this.streamAdapter = streamAdapter;
        this.aggregator = aggregator;
        this.bucketPersistence = bucketPersistence;
        this.nodeIdentityService = nodeIdentityService;
        this.tickerSourcePolicy = tickerSourcePolicy;
        this.acceptedEventCounter = meterRegistry.counter("spot_gex.stream.events.accepted");
        this.invalidEventCounter = meterRegistry.counter("spot_gex.stream.events.invalid");
        this.coalescedEventCounter = meterRegistry.counter("spot_gex.stream.events.coalesced");
        this.droppedEventCounter = meterRegistry.counter("spot_gex.stream.events.dropped");
        Gauge.builder("spot_gex.stream.active", this, service -> service.isStreamActive() ? 1.0d : 0.0d)
            .register(meterRegistry);
        Gauge.builder("spot_gex.stream.owner", this, service -> service.isStreamOwner() ? 1.0d : 0.0d)
            .register(meterRegistry);
        Gauge.builder("spot_gex.stream.connected", this, SpotGexStreamIngestionService::streamConnectedGaugeValue)
            .register(meterRegistry);
        Gauge.builder("spot_gex.stream.active_tickers", this, SpotGexStreamIngestionService::activeTickerCountGaugeValue)
            .register(meterRegistry);
        Gauge.builder("spot_gex.stream.pending_events", pendingEventGauge, AtomicInteger::get)
            .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!isStreamActive()) {
            log.info("[SPOT-GEX] Stream ingestion is standby because source mode is {}, node is {}, configured owner is {}",
                properties.getSource(), currentNodeId(), configuredOwnerNodeId());
            return;
        }

        streamAdapter.onEvent(this::handleEvent);
        reconcileSubscriptions();
        log.info("[SPOT-GEX] Stream ingestion started");
    }

    @Scheduled(fixedDelayString = "${spot-gex.collection.stream-reconcile-interval-ms:30000}")
    public void reconcileSubscriptions() {
        if (!isStreamActive()) {
            standDownSubscriptions();
            return;
        }

        Set<String> desiredTickers = new LinkedHashSet<>(
            tickerSourcePolicy.streamEligibleTickers(normalizeTickers(collectionConfigService.getSpotGexTickers()))
        );
        desiredTickers.stream()
            .filter(ticker -> !subscribedTickers.contains(ticker))
            .forEach(ticker -> {
                streamAdapter.subscribe(ticker);
                subscribedTickers.add(ticker);
            });

        subscribedTickers.stream()
            .filter(ticker -> !desiredTickers.contains(ticker))
            .toList()
            .forEach(ticker -> {
                streamAdapter.unsubscribe(ticker);
                subscribedTickers.remove(ticker);
            });
    }

    @Scheduled(fixedDelayString = "${spot-gex.collection.stream-flush-interval-ms:5000}")
    public void flushClosedBuckets() {
        flushClosedBuckets(System.currentTimeMillis());
    }

    void flushClosedBuckets(long watermarkMs) {
        if (!isStreamActive()) {
            return;
        }

        drainPendingEvents().forEach(aggregator::accept);
        List<SpotGexStreamBucket> buckets = aggregator.drainReadyBuckets(watermarkMs);
        if (buckets == null) {
            return;
        }
        if (buckets.isEmpty()) {
            return;
        }
        bucketPersistence.persistBuckets(buckets);
        lastFlushAt = Instant.now();
        buckets.stream()
            .map(bucket -> Instant.ofEpochMilli(bucket.bucketStartMs()))
            .max(Comparator.naturalOrder())
            .ifPresent(this::recordLastPersistedBucketStart);
    }

    public StreamRuntimeStatus getRuntimeStatusSnapshot() {
        return new StreamRuntimeStatus(
            isStreamMode(),
            isStreamActive(),
            isStreamOwner(),
            currentNodeId(),
            configuredOwnerNodeId(),
            streamAdapter.health(),
            lastEventAt,
            lastEventTicker,
            lastFlushAt,
            lastPersistedBucketStart,
            pendingEventGauge.get()
        );
    }

    private void handleEvent(SpotGexStreamEvent event) {
        if (!isStreamActive()) {
            return;
        }
        if (event != null && tickerSourcePolicy.isRestOnlyTicker(event.ticker())) {
            droppedEventCounter.increment();
            log.debug("[SPOT-GEX] Ignored REST-only ticker {} from stream source={}",
                event.ticker(), event.source());
            return;
        }
        if (!isValidEvent(event)) {
            invalidEventCounter.increment();
            log.debug("[SPOT-GEX] Rejected invalid stream event from source={} channel={}",
                event == null ? null : event.source(), event == null ? null : event.rawChannelName());
            return;
        }
        lastEventAt = Instant.ofEpochMilli(event.timestampMs());
        lastEventTicker = event.ticker();
        enqueueEvent(event);
    }

    private boolean isStreamMode() {
        return properties.getSource() == SpotGexCollectionProperties.SourceMode.STREAM;
    }

    private boolean isStreamActive() {
        return isStreamMode() && isStreamOwner();
    }

    private boolean isStreamOwner() {
        if (!isStreamMode()) {
            return false;
        }

        String configuredOwner = configuredOwnerNodeId();
        if (configuredOwner != null) {
            return configuredOwner.equalsIgnoreCase(currentNodeId());
        }

        return !activeProfiles().contains("active-active");
    }

    private String configuredOwnerNodeId() {
        String ownerNodeId = properties.getStreamOwnerNodeId();
        if (ownerNodeId == null || ownerNodeId.isBlank()) {
            return null;
        }
        return ownerNodeId.trim();
    }

    private String currentNodeId() {
        String nodeId = nodeIdentityService.getNodeId();
        return nodeId == null || nodeId.isBlank() ? "unknown" : nodeId;
    }

    private Collection<String> activeProfiles() {
        List<String> profiles = nodeIdentityService.getActiveProfiles();
        return profiles == null ? List.of() : profiles;
    }

    private boolean isValidEvent(SpotGexStreamEvent event) {
        if (event == null
            || event.ticker() == null
            || event.ticker().isBlank()
            || event.expiry() == null
            || event.strikePrice() == null
            || event.strikePrice().compareTo(BigDecimal.ZERO) <= 0
            || event.strikePriceCents() <= 0
            || event.timestampMs() <= 0
            || event.bucketStartMs() <= 0
            || event.ingestedAtMs() <= 0
            || event.underlyingPrice() == null) {
            return false;
        }
        return event.bucketStartMs() == event.timestampMs() - (event.timestampMs() % 60_000L);
    }

    private void enqueueEvent(SpotGexStreamEvent event) {
        synchronized (pendingEventsLock) {
            String bucketKey = event.bucketKey();
            SpotGexStreamEvent existing = pendingEvents.get(bucketKey);
            if (existing != null) {
                if (event.timestampMs() <= existing.timestampMs()) {
                    droppedEventCounter.increment();
                    return;
                }
                pendingEvents.put(bucketKey, event);
                acceptedEventCounter.increment();
                coalescedEventCounter.increment();
                pendingEventGauge.set(pendingEvents.size());
                return;
            }

            int maxPendingEvents = Math.max(1, properties.getStreamMaxPendingEvents());
            if (pendingEvents.size() >= maxPendingEvents) {
                evictOldestPendingEvent();
            }

            pendingEvents.put(bucketKey, event);
            acceptedEventCounter.increment();
            pendingEventGauge.set(pendingEvents.size());
        }
    }

    private void evictOldestPendingEvent() {
        if (pendingEvents.isEmpty()) {
            return;
        }
        String oldestKey = pendingEvents.keySet().iterator().next();
        pendingEvents.remove(oldestKey);
        droppedEventCounter.increment();
    }

    private List<SpotGexStreamEvent> drainPendingEvents() {
        synchronized (pendingEventsLock) {
            if (pendingEvents.isEmpty()) {
                return List.of();
            }
            List<SpotGexStreamEvent> drained = new ArrayList<>(pendingEvents.values());
            pendingEvents.clear();
            pendingEventGauge.set(0);
            return drained;
        }
    }

    private double streamConnectedGaugeValue() {
        SpotGexStreamHealth health = streamAdapter.health();
        return health != null && health.connected() ? 1.0d : 0.0d;
    }

    private double activeTickerCountGaugeValue() {
        SpotGexStreamHealth health = streamAdapter.health();
        return health == null ? 0.0d : health.activeTickerCount();
    }

    private Set<String> normalizeTickers(List<String> tickers) {
        Set<String> normalized = new LinkedHashSet<>();
        if (tickers == null) {
            return normalized;
        }

        for (String ticker : tickers) {
            if (ticker == null || ticker.isBlank()) {
                continue;
            }
            normalized.add(ticker.trim().toUpperCase(Locale.ROOT));
        }
        return normalized;
    }

    private void recordLastPersistedBucketStart(Instant bucketStart) {
        Instant current = lastPersistedBucketStart;
        if (current == null || bucketStart.isAfter(current)) {
            lastPersistedBucketStart = bucketStart;
        }
    }

    private void standDownSubscriptions() {
        drainPendingEvents();
        subscribedTickers.stream()
            .toList()
            .forEach(ticker -> {
                streamAdapter.unsubscribe(ticker);
                subscribedTickers.remove(ticker);
            });
    }

    public record StreamRuntimeStatus(
        boolean streamMode,
        boolean active,
        boolean owner,
        String nodeId,
        String ownerNodeId,
        SpotGexStreamHealth health,
        Instant lastEventAt,
        String lastEventTicker,
        Instant lastFlushAt,
        Instant lastPersistedBucketStart,
        int pendingEventCount
    ) {}
}
