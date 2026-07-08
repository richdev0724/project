package com.marketmono.service;

import com.marketmono.dto.SpotGexCollectionStatusDto;
import com.marketmono.dto.SpotGexSnapshotDto;
import com.marketmono.dto.SpotGexStreamStatusDto;
import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.repository.SpotGexExposureRepository;
import com.marketmono.repository.SpotGexStreamBucketRepository;
import com.marketmono.service.spotgex.SpotGexStreamHealth;
import com.marketmono.service.spotgex.SpotGexStreamIngestionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Query service for persisted spot GEX snapshots, history, and collection status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SpotGexQueryService {

    static final long MAX_FIVE_MINUTE_HISTORY_LOOKBACK_DAYS = 15;
    static final long MAX_UNFILTERED_FIVE_MINUTE_HISTORY_LOOKBACK_HOURS = 4;
    private static final String LATEST_SOURCE_PERSISTED = "persisted";
    private static final String LATEST_SOURCE_LIVE = "live";
    private static final String LATEST_SOURCE_STREAM = "stream";
    private static final String LATEST_SOURCE_REST = "rest";
    private static final String OPTION_CHAIN_EMPTY_CODE = "SPOT_GEX_OPTION_CHAIN_EMPTY";
    private static final String EXPOSURE_ROWS_EMPTY_CODE = "SPOT_GEX_EXPOSURE_ROWS_EMPTY";
    private static final String EXPOSURE_ROWS_INVALID_CODE = "SPOT_GEX_EXPOSURE_ROWS_INVALID";
    private static final String LIVE_FALLBACK_FAILED_CODE = "SPOT_GEX_LIVE_FALLBACK_FAILED";
    private static final String STREAM_BUCKET_EMPTY_CODE = "SPOT_GEX_STREAM_BUCKET_EMPTY";
    private static final String STREAM_INACTIVE_CODE = "SPOT_GEX_STREAM_INACTIVE";
    private static final String STREAM_DISCONNECTED_CODE = "SPOT_GEX_STREAM_DISCONNECTED";
    private static final String STREAM_TICKER_UNSUBSCRIBED_CODE = "SPOT_GEX_STREAM_TICKER_UNSUBSCRIBED";
    private static final String STREAM_FILTER_EMPTY_CODE = "SPOT_GEX_STREAM_FILTER_EMPTY";
    private static final String STREAM_STALE_CODE = "SPOT_GEX_STREAM_STALE";
    private static final String REST_ONLY_EMPTY_CODE = "SPOT_GEX_REST_ONLY_EMPTY";
    private static final String REST_ONLY_FILTER_EMPTY_CODE = "SPOT_GEX_REST_ONLY_FILTER_EMPTY";
    private static final String REST_ONLY_STALE_CODE = "SPOT_GEX_REST_ONLY_STALE";
    private static final String STREAM_HISTORY_UNAVAILABLE_CODE = "SPOT_GEX_STREAM_HISTORY_UNAVAILABLE";
    private static final String STREAM_HISTORY_UNAVAILABLE_MESSAGE =
        "Spot GEX stream history is temporarily unavailable. Latest stream snapshot data remains available.";
    private static final String REPLAY_SOURCE = "replay";
    private static final String REPLAY_TIME_ZONE = "America/New_York";
    private static final ZoneId REPLAY_ZONE_ID = ZoneId.of(REPLAY_TIME_ZONE);
    private static final LocalTime REPLAY_SESSION_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REPLAY_SESSION_CLOSE = LocalTime.of(16, 15);
    private static final int REPLAY_SESSION_LOOKBACK_DAYS = 7;
    private static final String REPLAY_SESSION_UNAVAILABLE_CODE = "SPOT_GEX_REPLAY_SESSION_UNAVAILABLE";
    private static final String REPLAY_TICKER_UNSTORED_CODE = "SPOT_GEX_REPLAY_TICKER_UNSTORED";
    private static final String REPLAY_BUCKET_MISSING_CODE = "SPOT_GEX_REPLAY_BUCKET_MISSING";
    private static final String WIDE_HISTORY_DEGRADATION_CODE = "SPOT_GEX_HISTORY_WINDOW_TOO_WIDE";
    private static final String WIDE_HISTORY_DEGRADATION_MESSAGE =
        "Spot GEX raw history is too wide for the requested range. Use expiry or strike filters, or a narrower range.";
    private static final Duration DEFAULT_LIVE_FALLBACK_CACHE_TTL = Duration.ofSeconds(45);
    private static final String COVERAGE_MODE_PERSISTED_SNAPSHOT = "persisted_snapshot";
    private static final String COVERAGE_MODE_STREAM_SNAPSHOT = "stream_snapshot";
    private static final String COVERAGE_MODE_STREAM_UNAVAILABLE = "stream_unavailable";
    private static final String COVERAGE_MODE_LIVE_FALLBACK = "live_fallback";
    private static final String FALLBACK_REASON_NONE = "none";
    private static final String FALLBACK_REASON_NO_PERSISTED_SNAPSHOT = "no_persisted_snapshot";
    private static final String FALLBACK_REASON_OUTSIDE_STREAM_COVERAGE = "outside_stream_coverage";
    private static final String FALLBACK_REASON_STREAM_SNAPSHOT_QUERY_FAILED = "stream_snapshot_query_failed";
    private static final String METRIC_LATEST_REQUESTS = "marketmono.spot.gex.latest.requests";
    private static final String METRIC_LATEST_CACHE_REQUESTS = "marketmono.spot.gex.latest.cache.requests";
    private static final String METRIC_LATEST_CACHE_LOAD_DURATION = "marketmono.spot.gex.latest.cache.load.duration";
    private static final String METRIC_LATEST_CACHE_COALESCED_WAITS = "marketmono.spot.gex.latest.cache.coalesced.waits";
    private static final String LATEST_SNAPSHOT_CACHE_NAME = "spotGexLatestSnapshotCache";
    private static final String LATEST_SNAPSHOT_CACHE_VERSION = "v1";
    private static final String CACHE_NULL_SENTINEL = "_";

    private final SpotGexExposureRepository repository;
    private final CollectionConfigService collectionConfigService;
    private final SpotGexCollectionService spotGexCollectionService;
    private final SpotGexFetchService spotGexFetchService;
    private final SpotGexStreamBucketRepository streamBucketRepository;
    private final SpotGexCollectionProperties properties;
    private final SpotGexStreamIngestionService streamIngestionService;
    private final StrataTickerFamilyResolver strataTickerFamilyResolver;
    private final SpotGexTickerSourcePolicy tickerSourcePolicy;
    private final Map<LiveFallbackCacheKey, CachedLatestSnapshotResult> liveFallbackCache = new ConcurrentHashMap<>();
    private final Map<LiveFallbackCacheKey, CompletableFuture<LatestSnapshotResult>> liveFallbackInFlight =
        new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<LatestSnapshotResult>> latestSnapshotInFlight = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();
    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    @Autowired(required = false)
    @Qualifier("spotGexLatestSnapshotCacheManager")
    private CacheManager spotGexLatestSnapshotCacheManager;

    public LatestSnapshotResult getLatestSnapshot(
            String ticker,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        LatestSnapshotResult result = getLatestSnapshotInternal(ticker, expiry, minStrike, maxStrike);
        recordLatestSnapshotMetric(result);
        return result;
    }

    private LatestSnapshotResult getLatestSnapshotInternal(
            String ticker,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        String normalizedTicker = ticker.toUpperCase(Locale.US).trim();
        if (isStreamMode()) {
            return getLatestStreamSnapshot(normalizedTicker, expiry, minStrike, maxStrike);
        }

        Optional<Instant> persistedCollectedAt = repository.findLatestCollectedAtByTicker(normalizedTicker);
        if (persistedCollectedAt.isPresent()) {
            Instant collectedAt = persistedCollectedAt.get();
            String cacheKey = latestSnapshotCacheKey(
                LATEST_SOURCE_PERSISTED,
                normalizedTicker,
                collectedAt,
                expiry,
                minStrike,
                maxStrike
            );
            return getLatestSnapshotWithCache(
                cacheKey,
                () -> LatestSnapshotResult.persisted(
                    collectedAt,
                    repository.findSnapshot(normalizedTicker, collectedAt, expiry, minStrike, maxStrike).stream()
                        .map(SpotGexSnapshotDto::from)
                        .toList()
                )
            );
        }

        return getLiveFallbackSnapshot(
            normalizedTicker,
            expiry,
            minStrike,
            maxStrike,
            FALLBACK_REASON_NO_PERSISTED_SNAPSHOT
        );
    }

    @Transactional(readOnly = true, timeout = 8)
    public List<SpotGexSnapshotDto> getHistory(
            String ticker,
            Instant since,
            String bucket,
            LocalDate expiry,
            BigDecimal strike) {
        return getHistory(ticker, since, bucket, expiry, strike, null, null);
    }

    @Transactional(readOnly = true, timeout = 8)
    public List<SpotGexSnapshotDto> getHistory(
            String ticker,
            Instant since,
            String bucket,
            LocalDate expiry,
            BigDecimal strike,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        return getHistoryResult(ticker, since, bucket, expiry, strike, minStrike, maxStrike).data();
    }

    @Transactional(readOnly = true, timeout = 8)
    public HistoryResult getHistoryResult(
            String ticker,
            Instant since,
            String bucket,
            LocalDate expiry,
            BigDecimal strike) {
        return getHistoryResult(ticker, since, bucket, expiry, strike, null, null);
    }

    @Transactional(readOnly = true, timeout = 8)
    public HistoryResult getHistoryResult(
            String ticker,
            Instant since,
            String bucket,
            LocalDate expiry,
            BigDecimal strike,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        String bucketInterval = resolveBucketInterval(bucket);
        if (isStreamMode()) {
            String normalizedTicker = ticker.toUpperCase(Locale.US);
            try {
                return HistoryResult.success(streamBucketRepository.findHistory(
                        normalizedTicker,
                        requireSince(since),
                        bucketInterval,
                        expiry,
                        strike,
                        minStrike,
                        maxStrike
                    ).stream()
                    .map(SpotGexSnapshotDto::from)
                    .toList());
            } catch (RuntimeException exception) {
                if (isStreamSnapshotQueryFailure(exception)) {
                    log.warn(
                        "[SPOT-GEX] Stream history query failed for {}; returning degraded response: {}",
                        normalizedTicker,
                        exception.getMessage()
                    );
                    return HistoryResult.degraded(
                        STREAM_HISTORY_UNAVAILABLE_CODE,
                        STREAM_HISTORY_UNAVAILABLE_MESSAGE
                    );
                }
                throw exception;
            }
        }

        if (shouldDegradeWideHistory(since, bucket, expiry, strike)) {
            return HistoryResult.degraded(WIDE_HISTORY_DEGRADATION_CODE, WIDE_HISTORY_DEGRADATION_MESSAGE);
        }

        Instant boundedSince = resolveBoundedSince(since, bucket);
        List<SpotGexSnapshotDto> history = repository.findHistory(ticker.toUpperCase(Locale.US), boundedSince, bucketInterval, expiry, strike, minStrike, maxStrike).stream()
            .map(SpotGexSnapshotDto::from)
            .toList();
        return HistoryResult.success(history);
    }

    @Transactional(readOnly = true, timeout = 5)
    public ReplaySessionResult getReplaySession(String ticker, LocalDate requestedDate) {
        String normalizedTicker = normalizeTicker(ticker);
        LocalDate effectiveRequestedDate = requestedDate != null ? requestedDate : defaultReplayRequestedDate();
        List<String> storedReplayRoots = storedReplayRoots(normalizedTicker);

        if (storedReplayRoots.isEmpty()) {
            return ReplaySessionResult.unstored(normalizedTicker, effectiveRequestedDate);
        }

        for (int offsetDays = 0; offsetDays < REPLAY_SESSION_LOOKBACK_DAYS; offsetDays++) {
            LocalDate candidateDate = effectiveRequestedDate.minusDays(offsetDays);
            ReplaySessionBounds bounds = replaySessionBounds(candidateDate);
            for (String replayRoot : storedReplayRoots) {
                List<Instant> bucketStarts = streamBucketRepository.findReplaySessionBucketStarts(
                    replayRoot,
                    bounds.openAt(),
                    bounds.closeAt()
                );
                if (bucketStarts != null && !bucketStarts.isEmpty()) {
                    return ReplaySessionResult.available(
                        normalizedTicker,
                        effectiveRequestedDate,
                        candidateDate,
                        bounds.openAt(),
                        bounds.closeAt(),
                        bucketStarts
                    );
                }
            }
        }

        return ReplaySessionResult.unavailable(normalizedTicker, effectiveRequestedDate);
    }

    @Transactional(readOnly = true, timeout = 8)
    public ReplaySnapshotResult getReplaySnapshot(
            String ticker,
            Instant at,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        String normalizedTicker = normalizeTicker(ticker);
        if (at == null) {
            throw new IllegalArgumentException("Replay snapshot at parameter is required.");
        }
        List<String> storedReplayRoots = storedReplayRoots(normalizedTicker);
        if (storedReplayRoots.isEmpty()) {
            return ReplaySnapshotResult.unstored(normalizedTicker, at);
        }

        Instant sessionOpenAt = replaySessionOpenAt(at);
        for (String replayRoot : storedReplayRoots) {
            List<SpotGexSnapshotDto> snapshots = streamBucketRepository.findReplaySnapshot(
                    replayRoot,
                    at,
                    sessionOpenAt,
                    expiry,
                    minStrike,
                    maxStrike
                ).stream()
                .map(SpotGexSnapshotDto::from)
                .map(snapshot -> snapshot.withTicker(normalizedTicker))
                .toList();
            if (!snapshots.isEmpty()) {
                return ReplaySnapshotResult.success(normalizedTicker, at, snapshots);
            }
        }

        return ReplaySnapshotResult.missingBucket(normalizedTicker, at);
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<SpotGexCollectionStatusDto> getCollectionStatus() {
        SpotGexCollectionProperties.SourceMode sourceMode = properties != null
            ? properties.getSource()
            : null;
        if (sourceMode == null) {
            sourceMode = SpotGexCollectionProperties.SourceMode.LEGACY;
        }
        boolean streamMode = sourceMode == SpotGexCollectionProperties.SourceMode.STREAM;
        long staleThresholdMs = properties != null ? properties.getStreamStaleThresholdMs() : 120_000L;
        if (staleThresholdMs <= 0L) {
            staleThresholdMs = 120_000L;
        }

        List<String> configuredTickers = collectionConfigService.getSpotGexTickers().stream()
            .map(ticker -> ticker.toUpperCase(Locale.US))
            .toList();
        if (configuredTickers.isEmpty()) {
            return List.of();
        }

        Map<String, SpotGexExposureRepository.SpotGexStatusProjection> persistedByTicker = new HashMap<>();
        for (SpotGexExposureRepository.SpotGexStatusProjection row : repository.findCollectionStatus(configuredTickers)) {
            persistedByTicker.put(row.getTicker().toUpperCase(Locale.US), row);
        }

        Map<String, SpotGexCollectionService.TickerRuntimeStatus> runtimeStatus =
            spotGexCollectionService.getRuntimeStatusSnapshot();

        Map<String, SpotGexStreamBucketRepository.StreamBucketStatusProjection> streamStatusByTicker = new HashMap<>();
        Set<String> subscribedTickers = Set.of();
        if (streamMode) {
            List<String> statusTickers = streamStatusTickersForCollectionStatus(configuredTickers);
            for (SpotGexStreamBucketRepository.StreamBucketStatusProjection row :
                streamBucketRepository.findStatusByTicker(statusTickers)) {
                streamStatusByTicker.put(row.ticker().toUpperCase(Locale.US), row);
            }

            SpotGexStreamIngestionService.StreamRuntimeStatus streamRuntime =
                streamIngestionService.getRuntimeStatusSnapshot();
            subscribedTickers = new HashSet<>(streamRuntime.health().subscribedTickers());
        }

        List<SpotGexCollectionStatusDto> statuses = new ArrayList<>(configuredTickers.size());
        Instant now = Instant.now();

        for (String ticker : configuredTickers) {
            String normalizedTicker = ticker.toUpperCase(Locale.US);
            boolean restOnlyTicker = streamMode && tickerSourcePolicy.isRestOnlyTicker(normalizedTicker);
            SpotGexExposureRepository.SpotGexStatusProjection persisted = persistedByTicker.get(normalizedTicker);
            SpotGexCollectionService.TickerRuntimeStatus runtime = restOnlyTicker
                ? restOnlyRuntimeStatus(normalizedTicker, runtimeStatus)
                : runtimeStatus.get(normalizedTicker);
            SpotGexStreamBucketRepository.StreamBucketStatusProjection streamStatus =
                restOnlyTicker
                    ? restOnlyStreamStatus(normalizedTicker, streamStatusByTicker)
                    : streamStatusByTicker.get(normalizedTicker);

            Instant latestStreamBucketStart = streamStatus != null ? streamStatus.latestBucketStart() : null;
            Instant lastCollectedAt = restOnlyTicker && latestStreamBucketStart != null
                ? latestStreamBucketStart
                : persisted != null ? persisted.getLastCollectedAt() : null;
            long collectionCycles = persisted != null && persisted.getCollectionCycles() != null
                ? persisted.getCollectionCycles()
                : restOnlyTicker && runtime != null && runtime.lastAttemptAt() != null ? 1L : 0L;
            String lastError = runtime != null ? runtime.lastError() : null;
            Long latestStreamBucketLagMillis = latestStreamBucketStart != null
                ? bucketLagMillis(latestStreamBucketStart, now)
                : null;
            boolean streamSubscribed = !restOnlyTicker && streamMode && subscribedTickers.contains(normalizedTicker);
            boolean streamStale = !restOnlyTicker && streamMode
                && (latestStreamBucketStart == null || latestStreamBucketLagMillis > staleThresholdMs);
            long streamRowsToday = streamStatus != null && streamStatus.rowsToday() != null
                ? streamStatus.rowsToday()
                : 0L;
            long streamRowsLatestSession = streamStatus != null && streamStatus.rowsLatestSession() != null
                ? streamStatus.rowsLatestSession()
                : 0L;
            long streamCurrentRows = streamStatus != null && streamStatus.currentRows() != null
                ? streamStatus.currentRows()
                : 0L;
            long rowsToday = restOnlyTicker
                ? streamCurrentRows
                : persisted != null && persisted.getRowsToday() != null ? persisted.getRowsToday() : 0L;
            boolean healthy = restOnlyTicker
                ? lastError == null && (runtime == null || runtime.lastCycleSuccessful())
                : streamMode
                    ? streamSubscribed && latestStreamBucketStart != null
                    : lastError == null;

            statuses.add(new SpotGexCollectionStatusDto(
                normalizedTicker,
                lastCollectedAt,
                rowsToday,
                collectionCycles,
                runtime != null ? runtime.lastAttemptAt() : null,
                runtime != null ? runtime.lastSuccessfulCollectionAt() : null,
                lastError,
                runtime != null ? runtime.errorCount() : 0,
                restOnlyTicker ? healthy : streamMode ? healthy : runtime == null || runtime.lastCycleSuccessful(),
                healthy,
                restOnlyTicker ? "rest_only" : sourceMode.name().toLowerCase(Locale.US),
                streamSubscribed,
                latestStreamBucketStart,
                latestStreamBucketLagMillis,
                streamStale,
                streamRowsToday,
                streamRowsLatestSession,
                streamCurrentRows
            ));
        }

        return statuses;
    }

    @Transactional(readOnly = true, timeout = 5)
    public SpotGexStreamStatusDto getStreamStatus() {
        SpotGexCollectionProperties.SourceMode sourceMode = properties != null
            ? properties.getSource()
            : SpotGexCollectionProperties.SourceMode.LEGACY;
        long staleThresholdMs = properties != null ? properties.getStreamStaleThresholdMs() : 120_000L;
        SpotGexStreamIngestionService.StreamRuntimeStatus runtime = streamIngestionService.getRuntimeStatusSnapshot();
        SpotGexStreamHealth health = runtime.health();
        List<String> configuredTickers = sourceMode == SpotGexCollectionProperties.SourceMode.STREAM
            ? tickerSourcePolicy.streamEligibleTickers(normalizedConfiguredTickers())
            : normalizedConfiguredTickers();
        Set<String> subscribedTickers = new HashSet<>(health.subscribedTickers());
        Instant now = Instant.now();
        boolean streamMode = sourceMode == SpotGexCollectionProperties.SourceMode.STREAM;

        List<SpotGexStreamStatusDto.TickerStatus> tickerStatuses = new ArrayList<>(configuredTickers.size());
        long staleTickerCount = 0;
        Instant latestBucketStart = null;

        for (String ticker : configuredTickers) {
            Optional<Instant> latestForTicker = streamMode
                ? streamBucketRepository.findLatestBucketStartByTicker(ticker)
                : Optional.empty();
            Instant tickerBucketStart = latestForTicker.orElse(null);
            Long lagMillis = tickerBucketStart != null ? bucketLagMillis(tickerBucketStart, now) : null;
            boolean stale = streamMode && (tickerBucketStart == null || lagMillis > staleThresholdMs);
            if (stale) {
                staleTickerCount++;
            }
            if (tickerBucketStart != null && (latestBucketStart == null || tickerBucketStart.isAfter(latestBucketStart))) {
                latestBucketStart = tickerBucketStart;
            }
            tickerStatuses.add(new SpotGexStreamStatusDto.TickerStatus(
                ticker,
                subscribedTickers.contains(ticker),
                tickerBucketStart,
                lagMillis,
                stale
            ));
        }

        Instant lastPersistedBucketStart = latestBucketStart != null
            ? latestBucketStart
            : runtime.lastPersistedBucketStart();
        Long lastPersistedBucketLagMillis = lastPersistedBucketStart != null
            ? bucketLagMillis(lastPersistedBucketStart, now)
            : null;

        return new SpotGexStreamStatusDto(
            sourceMode.name().toLowerCase(Locale.US),
            runtime.nodeId(),
            runtime.ownerNodeId(),
            runtime.owner(),
            health.source(),
            runtime.active(),
            health.connected(),
            health.activeTickerCount(),
            runtime.pendingEventCount(),
            health.subscribedTickers(),
            runtime.lastEventAt(),
            runtime.lastEventTicker(),
            runtime.lastFlushAt(),
            lastPersistedBucketStart,
            lastPersistedBucketLagMillis,
            streamMode && staleTickerCount > 0,
            staleThresholdMs,
            staleTickerCount,
            tickerStatuses
        );
    }

    private String resolveBucketInterval(String bucket) {
        return switch (bucket == null ? "5m" : bucket.toLowerCase(Locale.US)) {
            case "5m" -> "5 minutes";
            case "15m" -> "15 minutes";
            case "1h" -> "1 hour";
            default -> throw new IllegalArgumentException("Unsupported bucket: " + bucket);
        };
    }

    private Instant resolveBoundedSince(Instant since, String bucket) {
        if (since == null) {
            throw new IllegalArgumentException("History since parameter is required.");
        }

        String normalizedBucket = normalizeBucket(bucket);
        if (!"5m".equals(normalizedBucket)) {
            return since;
        }

        Instant earliestAllowed = Instant.now().minus(MAX_FIVE_MINUTE_HISTORY_LOOKBACK_DAYS, ChronoUnit.DAYS);
        return since.isBefore(earliestAllowed) ? earliestAllowed : since;
    }

    private boolean shouldDegradeWideHistory(Instant since, String bucket, LocalDate expiry, BigDecimal strike) {
        if (since == null || expiry != null || strike != null) {
            return false;
        }

        if (!"5m".equals(normalizeBucket(bucket))) {
            return false;
        }

        Instant earliestRawHistory = Instant.now().minus(MAX_UNFILTERED_FIVE_MINUTE_HISTORY_LOOKBACK_HOURS, ChronoUnit.HOURS);
        return since.isBefore(earliestRawHistory);
    }

    private String normalizeBucket(String bucket) {
        return bucket == null ? "5m" : bucket.toLowerCase(Locale.US);
    }

    private static String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required.");
        }
        return ticker.trim().toUpperCase(Locale.US);
    }

    private Instant requireSince(Instant since) {
        if (since == null) {
            throw new IllegalArgumentException("History since parameter is required.");
        }
        return since;
    }

    private boolean isStreamMode() {
        return properties != null && properties.getSource() == SpotGexCollectionProperties.SourceMode.STREAM;
    }

    private List<String> normalizedConfiguredTickers() {
        List<String> configuredTickers = collectionConfigService.getSpotGexTickers();
        if (configuredTickers == null) {
            return List.of();
        }
        return configuredTickers.stream()
            .filter(Objects::nonNull)
            .map(ticker -> ticker.trim().toUpperCase(Locale.US))
            .filter(ticker -> !ticker.isBlank())
            .toList();
    }

    private List<String> streamStatusTickersForCollectionStatus(List<String> configuredTickers) {
        LinkedHashSet<String> statusTickers = new LinkedHashSet<>(tickerSourcePolicy.streamEligibleTickers(configuredTickers));
        statusTickers.addAll(tickerSourcePolicy.collectionRootsForConfiguredTickers(configuredTickers));
        return List.copyOf(statusTickers);
    }

    private SpotGexCollectionService.TickerRuntimeStatus restOnlyRuntimeStatus(
            String displayTicker,
            Map<String, SpotGexCollectionService.TickerRuntimeStatus> runtimeStatus) {
        SpotGexCollectionService.TickerRuntimeStatus selected = null;
        for (String root : tickerSourcePolicy.storageRootsForDisplayTicker(displayTicker)) {
            SpotGexCollectionService.TickerRuntimeStatus candidate = runtimeStatus.get(root);
            if (candidate == null) {
                continue;
            }
            if (selected == null || isAfter(candidate.lastAttemptAt(), selected.lastAttemptAt())) {
                selected = candidate;
            }
        }
        return selected;
    }

    private SpotGexStreamBucketRepository.StreamBucketStatusProjection restOnlyStreamStatus(
            String displayTicker,
            Map<String, SpotGexStreamBucketRepository.StreamBucketStatusProjection> streamStatusByTicker) {
        Instant latestBucketStart = null;
        long rowsToday = 0L;
        long rowsLatestSession = 0L;
        long currentRows = 0L;
        boolean found = false;

        for (String root : tickerSourcePolicy.storageRootsForDisplayTicker(displayTicker)) {
            SpotGexStreamBucketRepository.StreamBucketStatusProjection status = streamStatusByTicker.get(root);
            if (status == null) {
                continue;
            }
            found = true;
            if (status.latestBucketStart() != null
                    && (latestBucketStart == null || status.latestBucketStart().isAfter(latestBucketStart))) {
                latestBucketStart = status.latestBucketStart();
            }
            rowsToday += status.rowsToday() == null ? 0L : status.rowsToday();
            rowsLatestSession += status.rowsLatestSession() == null ? 0L : status.rowsLatestSession();
            currentRows += status.currentRows() == null ? 0L : status.currentRows();
        }

        if (!found) {
            return null;
        }
        return new SpotGexStreamBucketRepository.StreamBucketStatusProjection(
            displayTicker,
            latestBucketStart,
            rowsToday,
            rowsLatestSession,
            currentRows
        );
    }

    private static boolean isAfter(Instant candidate, Instant current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isAfter(current);
    }

    private boolean isOutsideKnownConfiguredStreamTickers(String normalizedTicker) {
        try {
            List<String> configuredTickers = normalizedConfiguredTickers();
            return !configuredTickers.isEmpty() && !configuredTickers.contains(normalizedTicker);
        } catch (RuntimeException exception) {
            log.debug("[SPOT-GEX] Unable to resolve configured stream tickers: {}", exception.getMessage());
            return false;
        }
    }

    private boolean isStoredReplayTicker(String normalizedTicker) {
        return !storedReplayRoots(normalizedTicker).isEmpty();
    }

    private List<String> storedReplayRoots(String normalizedTicker) {
        List<String> configuredTickers = normalizedConfiguredTickers();
        if (configuredTickers.isEmpty()) {
            return List.of();
        }

        if (tickerSourcePolicy.isRestOnlyTicker(normalizedTicker)
                && tickerSourcePolicy.isRestOnlyFamilyConfigured(normalizedTicker, configuredTickers)) {
            return tickerSourcePolicy.storageRootsForDisplayTicker(normalizedTicker);
        }

        return strataTickerFamilyResolver.replayRoots(normalizedTicker).stream()
            .filter(configuredTickers::contains)
            .toList();
    }

    private static ReplaySessionBounds replaySessionBounds(LocalDate marketDate) {
        Instant openAt = marketDate.atTime(REPLAY_SESSION_OPEN).atZone(REPLAY_ZONE_ID).toInstant();
        Instant closeAt = marketDate.atTime(REPLAY_SESSION_CLOSE).atZone(REPLAY_ZONE_ID).toInstant();
        return new ReplaySessionBounds(openAt, closeAt);
    }

    private static Instant replaySessionOpenAt(Instant replayAt) {
        LocalDate marketDate = replayAt.atZone(REPLAY_ZONE_ID).toLocalDate();
        return replaySessionBounds(marketDate).openAt();
    }

    private LocalDate defaultReplayRequestedDate() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(REPLAY_ZONE_ID));
        LocalDate today = now.toLocalDate();

        if (isWeekend(today) || now.toLocalTime().isBefore(REPLAY_SESSION_OPEN)) {
            return previousWeekday(today.minusDays(1));
        }

        return today;
    }

    private static LocalDate previousWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (isWeekend(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static boolean hasReplayBucketGaps(List<Instant> bucketStarts) {
        if (bucketStarts == null || bucketStarts.size() < 2) {
            return false;
        }
        for (int index = 1; index < bucketStarts.size(); index++) {
            if (!bucketStarts.get(index - 1).plus(1, ChronoUnit.MINUTES).equals(bucketStarts.get(index))) {
                return true;
            }
        }
        return false;
    }

    private long bucketLagMillis(Instant bucketStart, Instant now) {
        return Math.max(0L, Duration.between(bucketStart, now).toMillis());
    }

    private long restOnlyStaleThresholdMs() {
        long staleThresholdMs = properties != null ? properties.getStreamStaleThresholdMs() : 120_000L;
        return staleThresholdMs > 0L ? staleThresholdMs : 120_000L;
    }

    private StreamSnapshotContext streamSnapshotContext(String normalizedTicker) {
        if (streamIngestionService == null) {
            return StreamSnapshotContext.unknown();
        }

        SpotGexStreamIngestionService.StreamRuntimeStatus runtime = streamIngestionRuntimeStatus();
        if (runtime == null || runtime.health() == null) {
            return StreamSnapshotContext.unknown();
        }

        SpotGexStreamHealth health = runtime.health();
        Set<String> subscribedTickers = new HashSet<>(
            health.subscribedTickers() == null ? List.of() : health.subscribedTickers()
        );
        long staleThresholdMs = properties != null ? properties.getStreamStaleThresholdMs() : 120_000L;
        return new StreamSnapshotContext(
            true,
            runtime.active(),
            health.connected(),
            subscribedTickers.contains(normalizedTicker),
            false,
            null,
            null,
            staleThresholdMs
        );
    }

    private SpotGexStreamIngestionService.StreamRuntimeStatus streamIngestionRuntimeStatus() {
        try {
            return streamIngestionService.getRuntimeStatusSnapshot();
        } catch (RuntimeException exception) {
            log.debug("[SPOT-GEX] Stream runtime status unavailable: {}", exception.getMessage());
            return null;
        }
    }

    private LatestSnapshotResult getLatestStreamSnapshot(
            String normalizedTicker,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        if (tickerSourcePolicy.isRestOnlyTicker(normalizedTicker)) {
            return getLatestRestOnlySnapshot(normalizedTicker, expiry, minStrike, maxStrike);
        }

        StreamSnapshotContext streamContext = streamSnapshotContext(normalizedTicker);
        boolean outsideKnownConfiguredStreamTickers = isOutsideKnownConfiguredStreamTickers(normalizedTicker);
        if (outsideKnownConfiguredStreamTickers) {
            return getLiveFallbackSnapshot(
                normalizedTicker,
                expiry,
                minStrike,
                maxStrike,
                FALLBACK_REASON_OUTSIDE_STREAM_COVERAGE
            );
        }
        if (streamContext.runtimeKnown() && Boolean.TRUE.equals(streamContext.active())) {
            if (!streamContext.subscribed()) {
                return LatestSnapshotResult.degradedStream(
                    STREAM_TICKER_UNSUBSCRIBED_CODE,
                    "Spot GEX stream is not subscribed for " + normalizedTicker + ".",
                    streamContext
                );
            }
        }

        Optional<Instant> bucketStart = streamBucketRepository.findLatestBucketStartByTicker(normalizedTicker);
        if (bucketStart.isEmpty()) {
            if (streamContext.runtimeKnown()
                    && Boolean.TRUE.equals(streamContext.active())
                    && !streamContext.connected()) {
                return LatestSnapshotResult.degradedStream(
                    STREAM_DISCONNECTED_CODE,
                    "Spot GEX stream transport is not connected.",
                    streamContext
                );
            }
            if (streamContext.runtimeKnown() && Boolean.FALSE.equals(streamContext.active())) {
                return LatestSnapshotResult.degradedStream(
                    STREAM_INACTIVE_CODE,
                    "Spot GEX stream ingestion is not active on this node.",
                    streamContext
                );
            }
            return LatestSnapshotResult.degradedStream(
                STREAM_BUCKET_EMPTY_CODE,
                "No stream-persisted Spot GEX bucket is available for " + normalizedTicker + ".",
                streamContext
            );
        }

        Instant snapshotAt = bucketStart.get();
        Long lagMillis = bucketLagMillis(snapshotAt, Instant.now());
        StreamSnapshotContext bucketContext = streamContext.withBucket(snapshotAt, lagMillis);
        String cacheKey = latestSnapshotCacheKey(
            LATEST_SOURCE_STREAM,
            normalizedTicker,
            snapshotAt,
            expiry,
            minStrike,
            maxStrike
        );
        return getLatestSnapshotWithCache(
            cacheKey,
            () -> loadLatestStreamSnapshot(normalizedTicker, snapshotAt, expiry, minStrike, maxStrike, bucketContext)
        );
    }

    private LatestSnapshotResult getLatestRestOnlySnapshot(
            String normalizedTicker,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        List<String> roots = tickerSourcePolicy.storageRootsForDisplayTicker(normalizedTicker);
        Map<RestOnlyCellKey, SpotGexSnapshotDto> mergedRows = new LinkedHashMap<>();
        Instant latestBucketStart = null;

        for (String root : roots) {
            Optional<Instant> rootBucketStart = streamBucketRepository.findLatestBucketStartByTicker(root);
            if (rootBucketStart.isEmpty()) {
                continue;
            }
            Instant bucketStart = rootBucketStart.get();
            if (latestBucketStart == null || bucketStart.isAfter(latestBucketStart)) {
                latestBucketStart = bucketStart;
            }
            streamBucketRepository.findSnapshot(root, bucketStart, expiry, minStrike, maxStrike).stream()
                .map(SpotGexSnapshotDto::from)
                .map(snapshot -> snapshot.withTicker(normalizedTicker))
                .forEach(snapshot -> {
                    RestOnlyCellKey key = RestOnlyCellKey.from(snapshot);
                    if (key != null) {
                        mergedRows.putIfAbsent(key, snapshot);
                    }
                });
        }

        if (latestBucketStart == null) {
            return new LatestSnapshotResult(
                null,
                List.of(),
                LATEST_SOURCE_REST,
                true,
                REST_ONLY_EMPTY_CODE,
                "No REST-persisted Spot GEX bucket is available for " + normalizedTicker + ".",
                null,
                null,
                null,
                false,
                false
            );
        }

        Long lagMillis = bucketLagMillis(latestBucketStart, Instant.now());
        boolean stale = lagMillis > restOnlyStaleThresholdMs();
        List<SpotGexSnapshotDto> snapshots = List.copyOf(mergedRows.values());
        if (snapshots.isEmpty()) {
            return new LatestSnapshotResult(
                latestBucketStart,
                snapshots,
                LATEST_SOURCE_REST,
                true,
                REST_ONLY_FILTER_EMPTY_CODE,
                "No REST-persisted Spot GEX rows matched the requested expiry or strike filter for " + normalizedTicker + ".",
                latestBucketStart,
                lagMillis,
                null,
                false,
                stale
            );
        }
        if (stale) {
            return new LatestSnapshotResult(
                latestBucketStart,
                snapshots,
                LATEST_SOURCE_REST,
                true,
                REST_ONLY_STALE_CODE,
                "The latest REST-persisted Spot GEX bucket for " + normalizedTicker + " is stale.",
                latestBucketStart,
                lagMillis,
                null,
                false,
                true
            );
        }
        return new LatestSnapshotResult(
            latestBucketStart,
            snapshots,
            LATEST_SOURCE_REST,
            false,
            null,
            null,
            latestBucketStart,
            lagMillis,
            null,
            false,
            false
        );
    }

    private LatestSnapshotResult loadLatestStreamSnapshot(
            String normalizedTicker,
            Instant snapshotAt,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike,
            StreamSnapshotContext bucketContext) {
        List<SpotGexSnapshotDto> snapshots;
        try {
            snapshots = streamBucketRepository.findSnapshot(normalizedTicker, snapshotAt, expiry, minStrike, maxStrike).stream()
                .map(SpotGexSnapshotDto::from)
                .toList();
        } catch (RuntimeException exception) {
            if (isStreamSnapshotQueryFailure(exception)) {
                log.warn(
                    "[SPOT-GEX] Stream latest snapshot query failed for {}; using live fallback: {}",
                    normalizedTicker,
                    exception.getMessage()
                );
                return getLiveFallbackSnapshot(
                    normalizedTicker,
                    expiry,
                    minStrike,
                    maxStrike,
                    FALLBACK_REASON_STREAM_SNAPSHOT_QUERY_FAILED
                );
            }
            throw exception;
        }
        if (snapshots.isEmpty()) {
            return LatestSnapshotResult.degradedStream(
                STREAM_FILTER_EMPTY_CODE,
                "No stream-persisted Spot GEX rows matched the requested expiry or strike filter for " + normalizedTicker + ".",
                bucketContext
            );
        }
        snapshots = enrichMissingStreamSnapshotFields(normalizedTicker, snapshots);
        if (bucketContext.stale()) {
            return LatestSnapshotResult.degradedStream(
                snapshotAt,
                snapshots,
                STREAM_STALE_CODE,
                "The latest stream-persisted Spot GEX bucket for " + normalizedTicker + " is stale.",
                bucketContext
            );
        }
        return LatestSnapshotResult.stream(snapshotAt, snapshots, bucketContext);
    }

    private LatestSnapshotResult getLatestSnapshotWithCache(
            String cacheKey,
            Supplier<LatestSnapshotResult> loader) {
        Cache cache = latestSnapshotCache();
        if (cache == null) {
            LatestSnapshotResult result = loadLatestSnapshotWithDuration(loader);
            recordLatestSnapshotCacheRequest("skipped", "cache_unavailable", result);
            return result;
        }

        CompletableFuture<LatestSnapshotResult> newFuture = new CompletableFuture<>();
        CompletableFuture<LatestSnapshotResult> existingFuture = latestSnapshotInFlight.putIfAbsent(cacheKey, newFuture);
        if (existingFuture != null) {
            recordLatestSnapshotCoalescedWait();
            recordLatestSnapshotCacheRequest("coalesced", "in_flight", null);
            return joinLatestSnapshot(existingFuture);
        }

        try {
            CacheLookupResult lookup = getLatestSnapshotCacheValue(cache, cacheKey);
            if (lookup.value() != null) {
                newFuture.complete(lookup.value());
                recordLatestSnapshotCacheRequest("hit", "cache", lookup.value());
                return lookup.value();
            }

            LatestSnapshotResult loaded = loadLatestSnapshotWithDuration(loader);
            if (isCacheableLatestSnapshot(loaded)) {
                String putFailureReason = putLatestSnapshotCacheValue(cache, cacheKey, loaded);
                if (putFailureReason != null) {
                    recordLatestSnapshotCacheRequest("put_failed", putFailureReason, loaded);
                } else if (lookup.failureReason() != null) {
                    recordLatestSnapshotCacheRequest("get_failed", lookup.failureReason(), loaded);
                } else {
                    recordLatestSnapshotCacheRequest("miss", "loaded", loaded);
                }
            } else if (lookup.failureReason() != null) {
                recordLatestSnapshotCacheRequest("get_failed", lookup.failureReason(), loaded);
            } else {
                recordLatestSnapshotCacheRequest("skipped", latestSnapshotCacheSkipReason(loaded), loaded);
            }
            newFuture.complete(loaded);
            return loaded;
        } catch (RuntimeException exception) {
            newFuture.completeExceptionally(exception);
            recordLatestSnapshotCacheRequest("failure", exception.getClass().getSimpleName(), null);
            throw exception;
        } finally {
            latestSnapshotInFlight.remove(cacheKey, newFuture);
        }
    }

    private LatestSnapshotResult loadLatestSnapshotWithDuration(Supplier<LatestSnapshotResult> loader) {
        long startedAtNanos = System.nanoTime();
        try {
            LatestSnapshotResult result = loader.get();
            recordLatestSnapshotCacheLoadDuration("success", startedAtNanos);
            return result;
        } catch (RuntimeException exception) {
            recordLatestSnapshotCacheLoadDuration("failure", startedAtNanos);
            throw exception;
        }
    }

    private Cache latestSnapshotCache() {
        return spotGexLatestSnapshotCacheManager == null
            ? null
            : spotGexLatestSnapshotCacheManager.getCache(LATEST_SNAPSHOT_CACHE_NAME);
    }

    private CacheLookupResult getLatestSnapshotCacheValue(Cache cache, String cacheKey) {
        try {
            return new CacheLookupResult(cache.get(cacheKey, LatestSnapshotResult.class), null);
        } catch (RuntimeException exception) {
            log.warn("[SPOT-GEX] Latest snapshot cache get failed for {}: {}", cacheKey, exception.getMessage());
            return new CacheLookupResult(null, exception.getClass().getSimpleName());
        }
    }

    private String putLatestSnapshotCacheValue(Cache cache, String cacheKey, LatestSnapshotResult result) {
        try {
            cache.put(cacheKey, result);
            return null;
        } catch (RuntimeException exception) {
            log.warn("[SPOT-GEX] Latest snapshot cache put failed for {}: {}", cacheKey, exception.getMessage());
            return exception.getClass().getSimpleName();
        }
    }

    private boolean isCacheableLatestSnapshot(LatestSnapshotResult result) {
        return result != null
            && (!result.degraded() || STREAM_STALE_CODE.equals(result.code()))
            && result.data() != null
            && !result.data().isEmpty();
    }

    private static boolean isStreamSnapshotQueryFailure(RuntimeException exception) {
        return exception instanceof DataAccessResourceFailureException
            || exception instanceof QueryTimeoutException
            || exception instanceof TransactionTimedOutException;
    }

    private boolean isCacheableLiveFallbackSnapshot(LatestSnapshotResult result) {
        return result != null
            && !result.degraded()
            && result.data() != null;
    }

    private String latestSnapshotCacheSkipReason(LatestSnapshotResult result) {
        if (result == null) {
            return "null_result";
        }
        if (result.degraded()) {
            return lowCardinalityValue(result.code());
        }
        if (result.data() == null || result.data().isEmpty()) {
            return "empty";
        }
        return "not_cacheable";
    }

    private LatestSnapshotResult joinLatestSnapshot(CompletableFuture<LatestSnapshotResult> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private static String latestSnapshotCacheKey(
            String source,
            String normalizedTicker,
            Instant snapshotAt,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        return String.join(
            "|",
            LATEST_SNAPSHOT_CACHE_VERSION,
            normalizeCacheKeyPart(source),
            normalizeCacheKeyPart(normalizedTicker),
            snapshotAt == null ? CACHE_NULL_SENTINEL : snapshotAt.toString(),
            expiry == null ? CACHE_NULL_SENTINEL : expiry.toString(),
            normalizeStrikeForCacheKey(minStrike),
            normalizeStrikeForCacheKey(maxStrike)
        );
    }

    private static String normalizeStrikeForCacheKey(BigDecimal value) {
        return value == null ? CACHE_NULL_SENTINEL : value.stripTrailingZeros().toPlainString();
    }

    private static String normalizeCacheKeyPart(String value) {
        return value == null || value.isBlank() ? CACHE_NULL_SENTINEL : value.trim().toLowerCase(Locale.US);
    }

    private List<SpotGexSnapshotDto> enrichMissingStreamSnapshotFields(
            String normalizedTicker,
            List<SpotGexSnapshotDto> snapshots) {
        if (!isStreamRestEnrichmentEnabled() || !hasMissingRestEnrichableFields(snapshots)) {
            return snapshots;
        }

        List<LocalDate> expirations = snapshots.stream()
            .map(SpotGexSnapshotDto::expiry)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (expirations.isEmpty()) {
            return snapshots;
        }

        try {
            List<SpotExposureByStrikeDto> restRows =
                spotGexFetchService.fetchAllSpotExposurePages(normalizedTicker, expirations);
            Map<DeltaMergeKey, SpotExposureByStrikeDto> restRowsByKey = restRowsByKey(restRows);
            if (restRowsByKey.isEmpty()) {
                return snapshots;
            }

            return snapshots.stream()
                .map(snapshot -> enrichMissingRestEnrichableFields(snapshot, restRowsByKey))
                .toList();
        } catch (RuntimeException exception) {
            log.warn("[SPOT-GEX] REST stream snapshot enrichment failed for {}: {}", normalizedTicker, exception.getMessage());
            return snapshots;
        }
    }

    private boolean isStreamRestEnrichmentEnabled() {
        return properties != null && properties.isStreamDeltaRestEnrichmentEnabled();
    }

    private static Map<DeltaMergeKey, SpotExposureByStrikeDto> restRowsByKey(List<SpotExposureByStrikeDto> restRows) {
        if (restRows == null || restRows.isEmpty()) {
            return Map.of();
        }

        Map<DeltaMergeKey, SpotExposureByStrikeDto> rowsByKey = new HashMap<>();
        for (SpotExposureByStrikeDto row : restRows) {
            DeltaMergeKey key = DeltaMergeKey.from(row);
            if (key != null && hasRestEnrichableFields(row)) {
                rowsByKey.putIfAbsent(key, row);
            }
        }
        return rowsByKey;
    }

    private static SpotGexSnapshotDto enrichMissingRestEnrichableFields(
            SpotGexSnapshotDto snapshot,
            Map<DeltaMergeKey, SpotExposureByStrikeDto> restRowsByKey) {
        if (!hasMissingRestEnrichableFields(snapshot)) {
            return snapshot;
        }

        SpotExposureByStrikeDto restRow = restRowsByKey.get(DeltaMergeKey.from(snapshot));
        return restRow == null ? snapshot : snapshot.withMissingFieldsFrom(restRow);
    }

    private static boolean hasMissingRestEnrichableFields(List<SpotGexSnapshotDto> snapshots) {
        return snapshots != null && snapshots.stream().anyMatch(SpotGexQueryService::hasMissingRestEnrichableFields);
    }

    private static boolean hasMissingRestEnrichableFields(SpotGexSnapshotDto snapshot) {
        return snapshot != null && (
            snapshot.callDeltaOi() == null
                || snapshot.putDeltaOi() == null
                || snapshot.callGammaOi() == null
                || snapshot.putGammaOi() == null
                || snapshot.callVannaOi() == null
                || snapshot.putVannaOi() == null
                || snapshot.callCharmOi() == null
                || snapshot.putCharmOi() == null
                || snapshot.callDeltaVol() == null
                || snapshot.putDeltaVol() == null
                || snapshot.callGammaVol() == null
                || snapshot.putGammaVol() == null
                || snapshot.callVannaVol() == null
                || snapshot.putVannaVol() == null
                || snapshot.callCharmVol() == null
                || snapshot.putCharmVol() == null
                || snapshot.callDeltaBid() == null
                || snapshot.callDeltaAsk() == null
                || snapshot.callGammaBid() == null
                || snapshot.callGammaAsk() == null
                || snapshot.callVannaBid() == null
                || snapshot.callVannaAsk() == null
                || snapshot.callCharmBid() == null
                || snapshot.callCharmAsk() == null
                || snapshot.putDeltaBid() == null
                || snapshot.putDeltaAsk() == null
                || snapshot.putGammaBid() == null
                || snapshot.putGammaAsk() == null
                || snapshot.putVannaBid() == null
                || snapshot.putVannaAsk() == null
                || snapshot.putCharmBid() == null
                || snapshot.putCharmAsk() == null
        );
    }

    private static boolean hasRestEnrichableFields(SpotExposureByStrikeDto row) {
        return row != null && (
            row.callDeltaOi() != null
                || row.putDeltaOi() != null
                || row.callGammaOi() != null
                || row.putGammaOi() != null
                || row.callVannaOi() != null
                || row.putVannaOi() != null
                || row.callCharmOi() != null
                || row.putCharmOi() != null
                || row.callDeltaVol() != null
                || row.putDeltaVol() != null
                || row.callGammaVol() != null
                || row.putGammaVol() != null
                || row.callVannaVol() != null
                || row.putVannaVol() != null
                || row.callCharmVol() != null
                || row.putCharmVol() != null
                || row.callDeltaBid() != null
                || row.callDeltaAsk() != null
                || row.callGammaBid() != null
                || row.callGammaAsk() != null
                || row.callVannaBid() != null
                || row.callVannaAsk() != null
                || row.callCharmBid() != null
                || row.callCharmAsk() != null
                || row.putDeltaBid() != null
                || row.putDeltaAsk() != null
                || row.putGammaBid() != null
                || row.putGammaAsk() != null
                || row.putVannaBid() != null
                || row.putVannaAsk() != null
                || row.putCharmBid() != null
                || row.putCharmAsk() != null
        );
    }

    private LatestSnapshotResult getLiveFallbackSnapshot(
            String normalizedTicker,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike,
            String fallbackReason) {
        LiveFallbackCacheKey cacheKey = new LiveFallbackCacheKey(normalizedTicker, expiry, minStrike, maxStrike);
        Instant now = Instant.now();
        CachedLatestSnapshotResult cached = liveFallbackCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.result();
        }

        CompletableFuture<LatestSnapshotResult> newFuture = new CompletableFuture<>();
        CompletableFuture<LatestSnapshotResult> existingFuture = liveFallbackInFlight.putIfAbsent(cacheKey, newFuture);
        if (existingFuture != null) {
            return joinLiveFallback(existingFuture);
        }

        try {
            LatestSnapshotResult result = loadLiveFallbackSnapshot(
                normalizedTicker,
                expiry,
                minStrike,
                maxStrike,
                fallbackReason
            );
            if (isCacheableLiveFallbackSnapshot(result)) {
                liveFallbackCache.put(cacheKey, new CachedLatestSnapshotResult(result, now.plus(liveFallbackCacheTtl())));
            }
            newFuture.complete(result);
            return result;
        } catch (RuntimeException exception) {
            newFuture.completeExceptionally(exception);
            throw exception;
        } finally {
            liveFallbackInFlight.remove(cacheKey, newFuture);
        }
    }

    private LatestSnapshotResult joinLiveFallback(CompletableFuture<LatestSnapshotResult> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private Duration liveFallbackCacheTtl() {
        long configuredTtlMs = properties != null ? properties.getLiveFallbackCacheTtlMs() : 0L;
        return configuredTtlMs > 0L ? Duration.ofMillis(configuredTtlMs) : DEFAULT_LIVE_FALLBACK_CACHE_TTL;
    }

    private LatestSnapshotResult loadLiveFallbackSnapshot(
            String normalizedTicker,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike,
            String fallbackReason) {
        try {
            List<LocalDate> expirations = expiry == null
                ? spotGexFetchService.getExpirationDates(normalizedTicker, null)
                : List.of(expiry);
            if (expirations.isEmpty()) {
                return LatestSnapshotResult.degradedLive(
                    OPTION_CHAIN_EMPTY_CODE,
                    "No option chain expirations are available for " + normalizedTicker + ".",
                    fallbackReason
                );
            }

            List<SpotExposureByStrikeDto> liveRows =
                spotGexFetchService.fetchAllSpotExposurePages(normalizedTicker, expirations);
            if (liveRows.isEmpty()) {
                return LatestSnapshotResult.degradedLive(
                    EXPOSURE_ROWS_EMPTY_CODE,
                    "No live Spot GEX exposure rows are available for " + normalizedTicker + ".",
                    fallbackReason
                );
            }

            Instant collectedAt = Instant.now();
            List<SpotExposureByStrikeDto> usableRows = liveRows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.expiry() != null && row.strike() != null)
                .toList();
            if (usableRows.isEmpty()) {
                return LatestSnapshotResult.degradedLive(
                    EXPOSURE_ROWS_INVALID_CODE,
                    "Live Spot GEX exposure rows for " + normalizedTicker + " did not include usable expiry and strike values.",
                    fallbackReason
                );
            }

            List<SpotGexSnapshotDto> snapshots = usableRows.stream()
                .filter(row -> expiry == null || expiry.equals(row.expiry()))
                .filter(row -> minStrike == null || row.strike().compareTo(minStrike) >= 0)
                .filter(row -> maxStrike == null || row.strike().compareTo(maxStrike) <= 0)
                .map(row -> SpotGexSnapshotDto.from(row, normalizedTicker, collectedAt))
                .toList();

            if (snapshots.isEmpty()) {
                return LatestSnapshotResult.live(collectedAt, List.of(), fallbackReason);
            }

            log.info("[SPOT-GEX] Served {} live fallback rows for {} with reason {}",
                snapshots.size(), normalizedTicker, fallbackReason);
            return LatestSnapshotResult.live(collectedAt, snapshots, fallbackReason);
        } catch (RuntimeException exception) {
            log.warn("[SPOT-GEX] Live fallback failed for {}: {}", normalizedTicker, exception.getMessage());
            return LatestSnapshotResult.degradedLive(
                LIVE_FALLBACK_FAILED_CODE,
                "Unable to load live Spot GEX data for " + normalizedTicker + ". Retry shortly.",
                fallbackReason
            );
        }
    }

    private void recordLatestSnapshotMetric(LatestSnapshotResult result) {
        if (meterRegistry == null || result == null) {
            return;
        }

        Counter.builder(METRIC_LATEST_REQUESTS)
            .tag("source_mode", lowCardinalityValue(result.source()))
            .tag("fallback_reason", lowCardinalityValue(result.fallbackReason()))
            .tag("coverage_class", lowCardinalityValue(result.coverageMode()))
            .tag("result", result.degraded() ? "degraded" : "success")
            .register(meterRegistry)
            .increment();
    }

    private void recordLatestSnapshotCacheRequest(String outcome, String reason, LatestSnapshotResult result) {
        if (meterRegistry == null) {
            return;
        }

        Counter.builder(METRIC_LATEST_CACHE_REQUESTS)
            .tag("outcome", lowCardinalityValue(outcome))
            .tag("reason", lowCardinalityValue(reason))
            .tag("source_mode", result == null ? "unknown" : lowCardinalityValue(result.source()))
            .tag("result", result == null ? "unknown" : result.degraded() ? "degraded" : "success")
            .register(meterRegistry)
            .increment();
    }

    private void recordLatestSnapshotCacheLoadDuration(String outcome, long startedAtNanos) {
        if (meterRegistry == null) {
            return;
        }

        Timer.builder(METRIC_LATEST_CACHE_LOAD_DURATION)
            .tag("outcome", lowCardinalityValue(outcome))
            .register(meterRegistry)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    private void recordLatestSnapshotCoalescedWait() {
        if (meterRegistry == null) {
            return;
        }

        Counter.builder(METRIC_LATEST_CACHE_COALESCED_WAITS)
            .register(meterRegistry)
            .increment();
    }

    private static String lowCardinalityValue(String value) {
        return value == null || value.isBlank() ? FALLBACK_REASON_NONE : value.trim().toLowerCase(Locale.US);
    }

    private static String coverageModeFor(String source, boolean degraded, List<SpotGexSnapshotDto> data) {
        if (LATEST_SOURCE_LIVE.equals(source)) {
            return COVERAGE_MODE_LIVE_FALLBACK;
        }
        if (LATEST_SOURCE_STREAM.equals(source)) {
            return degraded && (data == null || data.isEmpty())
                ? COVERAGE_MODE_STREAM_UNAVAILABLE
                : COVERAGE_MODE_STREAM_SNAPSHOT;
        }
        return COVERAGE_MODE_PERSISTED_SNAPSHOT;
    }

    private static int rowCount(List<SpotGexSnapshotDto> data) {
        return data == null ? 0 : data.size();
    }

    private static int availableExpiryCount(List<SpotGexSnapshotDto> data) {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        return (int) data.stream()
            .map(SpotGexSnapshotDto::expiry)
            .filter(Objects::nonNull)
            .distinct()
            .count();
    }

    private static int availableStrikeCount(List<SpotGexSnapshotDto> data) {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        return (int) data.stream()
            .map(SpotGexSnapshotDto::strike)
            .filter(Objects::nonNull)
            .distinct()
            .count();
    }

    private record LiveFallbackCacheKey(
        String ticker,
        LocalDate expiry,
        BigDecimal minStrike,
        BigDecimal maxStrike
    ) {}

    private record RestOnlyCellKey(LocalDate expiry, BigDecimal strike) {
        static RestOnlyCellKey from(SpotGexSnapshotDto snapshot) {
            if (snapshot == null || snapshot.expiry() == null || snapshot.strike() == null) {
                return null;
            }
            return new RestOnlyCellKey(snapshot.expiry(), snapshot.strike().stripTrailingZeros());
        }
    }

    private record CachedLatestSnapshotResult(LatestSnapshotResult result, Instant expiresAt) {}

    private record CacheLookupResult(LatestSnapshotResult value, String failureReason) {}

    private record ReplaySessionBounds(Instant openAt, Instant closeAt) {}

    private record DeltaMergeKey(LocalDate expiry, BigDecimal strike) {
        static DeltaMergeKey from(SpotGexSnapshotDto snapshot) {
            return snapshot == null ? null : from(snapshot.expiry(), snapshot.strike());
        }

        static DeltaMergeKey from(SpotExposureByStrikeDto row) {
            return row == null ? null : from(row.expiry(), row.strike());
        }

        private static DeltaMergeKey from(LocalDate expiry, BigDecimal strike) {
            if (expiry == null || strike == null) {
                return null;
            }
            return new DeltaMergeKey(expiry, strike.stripTrailingZeros());
        }
    }

    public record LatestSnapshotResult(
        Instant collectedAt,
        List<SpotGexSnapshotDto> data,
        String source,
        boolean degraded,
        String code,
        String message,
        Instant latestBucketStart,
        Long latestBucketLagMillis,
        Boolean streamConnected,
        Boolean streamSubscribed,
        Boolean streamStale,
        String coverageMode,
        String fallbackReason,
        int rowCount,
        int availableExpiryCount,
        int availableStrikeCount
    ) {
        public LatestSnapshotResult(
                Instant collectedAt,
                List<SpotGexSnapshotDto> data,
                String source,
                boolean degraded,
                String code,
                String message,
                Instant latestBucketStart,
                Long latestBucketLagMillis,
                Boolean streamConnected,
                Boolean streamSubscribed,
                Boolean streamStale) {
            this(
                collectedAt,
                data,
                source,
                degraded,
                code,
                message,
                latestBucketStart,
                latestBucketLagMillis,
                streamConnected,
                streamSubscribed,
                streamStale,
                coverageModeFor(source, degraded, data),
                null,
                SpotGexQueryService.rowCount(data),
                SpotGexQueryService.availableExpiryCount(data),
                SpotGexQueryService.availableStrikeCount(data)
            );
        }

        public LatestSnapshotResult(Instant collectedAt, List<SpotGexSnapshotDto> data) {
            this(collectedAt, data, LATEST_SOURCE_PERSISTED, false, null, null, null, null, null, null, null);
        }

        public static LatestSnapshotResult persisted(Instant collectedAt, List<SpotGexSnapshotDto> data) {
            return new LatestSnapshotResult(collectedAt, data, LATEST_SOURCE_PERSISTED, false, null, null, null, null, null, null, null);
        }

        public static LatestSnapshotResult live(Instant collectedAt, List<SpotGexSnapshotDto> data) {
            return new LatestSnapshotResult(collectedAt, data, LATEST_SOURCE_LIVE, false, null, null, null, null, null, null, null);
        }

        public static LatestSnapshotResult live(
                Instant collectedAt,
                List<SpotGexSnapshotDto> data,
                String fallbackReason) {
            return withMetadata(
                collectedAt,
                data,
                LATEST_SOURCE_LIVE,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                COVERAGE_MODE_LIVE_FALLBACK,
                fallbackReason
            );
        }

        public static LatestSnapshotResult stream(Instant collectedAt, List<SpotGexSnapshotDto> data) {
            return new LatestSnapshotResult(collectedAt, data, LATEST_SOURCE_STREAM, false, null, null, null, null, null, null, null);
        }

        public static LatestSnapshotResult stream(
                Instant collectedAt,
                List<SpotGexSnapshotDto> data,
                StreamSnapshotContext context) {
            return new LatestSnapshotResult(
                collectedAt,
                data,
                LATEST_SOURCE_STREAM,
                false,
                null,
                null,
                context.latestBucketStart(),
                context.latestBucketLagMillis(),
                context.connected(),
                context.subscribed(),
                context.stale()
            );
        }

        public static LatestSnapshotResult degradedLive(String code, String message) {
            return new LatestSnapshotResult(null, List.of(), LATEST_SOURCE_LIVE, true, code, message, null, null, null, null, null);
        }

        public static LatestSnapshotResult degradedLive(String code, String message, String fallbackReason) {
            return withMetadata(
                null,
                List.of(),
                LATEST_SOURCE_LIVE,
                true,
                code,
                message,
                null,
                null,
                null,
                null,
                null,
                COVERAGE_MODE_LIVE_FALLBACK,
                fallbackReason
            );
        }

        public static LatestSnapshotResult degradedStream(String code, String message) {
            return new LatestSnapshotResult(null, List.of(), LATEST_SOURCE_STREAM, true, code, message, null, null, null, null, null);
        }

        public static LatestSnapshotResult degradedStream(String code, String message, StreamSnapshotContext context) {
            return degradedStream(null, List.of(), code, message, context);
        }

        public static LatestSnapshotResult degradedStream(
                Instant collectedAt,
                List<SpotGexSnapshotDto> data,
                String code,
                String message,
                StreamSnapshotContext context) {
            return new LatestSnapshotResult(
                collectedAt,
                data,
                LATEST_SOURCE_STREAM,
                true,
                code,
                message,
                context.latestBucketStart(),
                context.latestBucketLagMillis(),
                context.connected(),
                context.subscribed(),
                context.stale()
            );
        }

        private static LatestSnapshotResult withMetadata(
                Instant collectedAt,
                List<SpotGexSnapshotDto> data,
                String source,
                boolean degraded,
                String code,
                String message,
                Instant latestBucketStart,
                Long latestBucketLagMillis,
                Boolean streamConnected,
                Boolean streamSubscribed,
                Boolean streamStale,
                String coverageMode,
                String fallbackReason) {
            return new LatestSnapshotResult(
                collectedAt,
                data,
                source,
                degraded,
                code,
                message,
                latestBucketStart,
                latestBucketLagMillis,
                streamConnected,
                streamSubscribed,
                streamStale,
                coverageMode,
                fallbackReason,
                SpotGexQueryService.rowCount(data),
                SpotGexQueryService.availableExpiryCount(data),
                SpotGexQueryService.availableStrikeCount(data)
            );
        }
    }

    private record StreamSnapshotContext(
        boolean runtimeKnown,
        Boolean active,
        Boolean connected,
        Boolean subscribed,
        Boolean stale,
        Instant latestBucketStart,
        Long latestBucketLagMillis,
        long staleThresholdMs
    ) {
        static StreamSnapshotContext unknown() {
            return new StreamSnapshotContext(false, null, null, null, null, null, null, 120_000L);
        }

        StreamSnapshotContext withBucket(Instant bucketStart, Long lagMillis) {
            boolean bucketStale = lagMillis != null && lagMillis > staleThresholdMs;
            return new StreamSnapshotContext(
                runtimeKnown,
                active,
                connected,
                subscribed,
                bucketStale,
                bucketStart,
                lagMillis,
                staleThresholdMs
            );
        }
    }

    public record HistoryResult(
        List<SpotGexSnapshotDto> data,
        boolean degraded,
        String degradationCode,
        String degradationMessage
    ) {
        public static HistoryResult success(List<SpotGexSnapshotDto> data) {
            return new HistoryResult(data, false, null, null);
        }

        public static HistoryResult degraded(String code, String message) {
            return new HistoryResult(List.of(), true, code, message);
        }
    }

    public record ReplaySessionResult(
        String ticker,
        boolean stored,
        LocalDate requestedDate,
        LocalDate marketDate,
        String timeZone,
        Instant openAt,
        Instant closeAt,
        Instant latestBucketStart,
        List<Instant> availableBucketStarts,
        boolean hasGaps,
        boolean degraded,
        String code,
        String message
    ) {
        public ReplaySessionResult {
            availableBucketStarts = availableBucketStarts == null ? List.of() : List.copyOf(availableBucketStarts);
        }

        static ReplaySessionResult available(
                String ticker,
                LocalDate requestedDate,
                LocalDate marketDate,
                Instant openAt,
                Instant closeAt,
                List<Instant> availableBucketStarts) {
            List<Instant> buckets = availableBucketStarts == null ? List.of() : List.copyOf(availableBucketStarts);
            Instant latestBucketStart = buckets.isEmpty() ? null : buckets.getLast();
            return new ReplaySessionResult(
                ticker,
                true,
                requestedDate,
                marketDate,
                REPLAY_TIME_ZONE,
                openAt,
                closeAt,
                latestBucketStart,
                buckets,
                hasReplayBucketGaps(buckets),
                false,
                null,
                null
            );
        }

        static ReplaySessionResult unavailable(String ticker, LocalDate requestedDate) {
            return new ReplaySessionResult(
                ticker,
                true,
                requestedDate,
                null,
                REPLAY_TIME_ZONE,
                null,
                null,
                null,
                List.of(),
                false,
                true,
                REPLAY_SESSION_UNAVAILABLE_CODE,
                "No stored Spot GEX replay session is available for " + ticker + " within retained 1-minute buckets."
            );
        }

        static ReplaySessionResult unstored(String ticker, LocalDate requestedDate) {
            return new ReplaySessionResult(
                ticker,
                false,
                requestedDate,
                null,
                REPLAY_TIME_ZONE,
                null,
                null,
                null,
                List.of(),
                false,
                true,
                REPLAY_TICKER_UNSTORED_CODE,
                "Replay is unavailable for " + ticker + " because it is outside stored Spot GEX collection coverage."
            );
        }
    }

    public record ReplaySnapshotResult(
        boolean success,
        String ticker,
        String source,
        Instant snapshotAt,
        LocalDate marketDate,
        String timeZone,
        int rowCount,
        int availableExpiryCount,
        int availableStrikeCount,
        List<SpotGexSnapshotDto> data,
        boolean degraded,
        String code,
        String message
    ) {
        public ReplaySnapshotResult {
            data = data == null ? List.of() : List.copyOf(data);
        }

        static ReplaySnapshotResult success(String ticker, Instant snapshotAt, List<SpotGexSnapshotDto> data) {
            LocalDate marketDate = data == null || data.isEmpty() ? null : data.getFirst().marketDate();
            return new ReplaySnapshotResult(
                true,
                ticker,
                REPLAY_SOURCE,
                snapshotAt,
                marketDate,
                REPLAY_TIME_ZONE,
                SpotGexQueryService.rowCount(data),
                SpotGexQueryService.availableExpiryCount(data),
                SpotGexQueryService.availableStrikeCount(data),
                data,
                false,
                null,
                null
            );
        }

        static ReplaySnapshotResult missingBucket(String ticker, Instant snapshotAt) {
            return new ReplaySnapshotResult(
                false,
                ticker,
                REPLAY_SOURCE,
                snapshotAt,
                null,
                REPLAY_TIME_ZONE,
                0,
                0,
                0,
                List.of(),
                true,
                REPLAY_BUCKET_MISSING_CODE,
                "No stored Spot GEX replay bucket is available for " + ticker + " at " + snapshotAt + "."
            );
        }

        static ReplaySnapshotResult unstored(String ticker, Instant snapshotAt) {
            return new ReplaySnapshotResult(
                true,
                ticker,
                REPLAY_SOURCE,
                snapshotAt,
                null,
                REPLAY_TIME_ZONE,
                0,
                0,
                0,
                List.of(),
                true,
                REPLAY_TICKER_UNSTORED_CODE,
                "Replay is unavailable for " + ticker + " because it is outside stored Spot GEX collection coverage."
            );
        }
    }
}
