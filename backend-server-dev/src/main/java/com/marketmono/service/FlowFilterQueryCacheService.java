package com.marketmono.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.marketmono.config.OptionTradesProperties;
import com.marketmono.dto.OptionTradeCountMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
@Slf4j
public class FlowFilterQueryCacheService {

    private static final int MAX_ENTRIES = 512;
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);
    private static final Duration STALE_TTL = Duration.ofMinutes(2);
    private static final Duration NEGATIVE_COOLDOWN_TTL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_PRODUCER_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final CacheSettings DEFAULT_CACHE_SETTINGS =
        new CacheSettings(MAX_ENTRIES, CACHE_TTL, STALE_TTL, NEGATIVE_COOLDOWN_TTL);
    private static final String METRIC_REQUESTS = "flow.filters.query.cache.requests";
    private static final String METRIC_PRODUCER_DURATION = "flow.filters.query.cache.producer.duration";
    private static final String METRIC_VISIBLE_RESPONSES = "flow.filters.query.cache.visible.responses";
    private static final String METRIC_STALE_SERVED = "flow.filters.query.cache.stale.served";
    private static final String METRIC_NEGATIVE_COOLDOWN_RESPONSES =
        "flow.filters.query.cache.negative_cooldown.responses";
    private static final String TAG_ENDPOINT = "endpoint";
    private static final String TAG_OUTCOME = "outcome";
    private static final String TAG_FILTER_SHAPE = "filter_shape";
    private static final String TAG_COUNT_MODE = "count_mode";
    private static final String TAG_ROLE = "role";
    private static final String TAG_CAUSE = "cause";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_STALE_ON_ERROR = "stale_on_error";
    private static final String OUTCOME_NEGATIVE_COOLDOWN = "negative_cooldown";
    private static final String OUTCOME_PRODUCER_ERROR = "producer_error";
    private static final String ROLE_PRODUCER = "producer";
    private static final String ROLE_STALE = "stale";
    private static final String ROLE_WAITER = "waiter";

    private final Cache<FlowFilterQueryCacheKey, Object> responseCache;
    private final Cache<FlowFilterQueryCacheKey, Object> staleResponseCache;
    private final Cache<FlowFilterQueryCacheKey, FlowFilterNegativeCooldown> negativeCooldownCache;
    private final ConcurrentMap<FlowFilterQueryCacheKey, CompletableFuture<FlowFilterCacheResult>> inFlight =
        new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final Duration producerWaitTimeout;
    private final int negativeCooldownRetryAfterSeconds;

    public FlowFilterQueryCacheService() {
        this(null, DEFAULT_PRODUCER_WAIT_TIMEOUT, DEFAULT_CACHE_SETTINGS);
    }

    @Autowired
    public FlowFilterQueryCacheService(
        ObjectProvider<MeterRegistry> meterRegistryProvider,
        OptionTradesProperties optionTradesProperties
    ) {
        this(
            meterRegistryProvider.getIfAvailable(),
            DEFAULT_PRODUCER_WAIT_TIMEOUT,
            cacheSettings(optionTradesProperties)
        );
    }

    public FlowFilterQueryCacheService(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider.getIfAvailable(), DEFAULT_PRODUCER_WAIT_TIMEOUT, DEFAULT_CACHE_SETTINGS);
    }

    public FlowFilterQueryCacheService(MeterRegistry meterRegistry) {
        this(meterRegistry, DEFAULT_PRODUCER_WAIT_TIMEOUT, DEFAULT_CACHE_SETTINGS);
    }

    FlowFilterQueryCacheService(MeterRegistry meterRegistry, Duration producerWaitTimeout) {
        this(meterRegistry, producerWaitTimeout, DEFAULT_CACHE_SETTINGS);
    }

    FlowFilterQueryCacheService(
        MeterRegistry meterRegistry,
        Duration producerWaitTimeout,
        Duration freshTtl,
        Duration staleTtl,
        Duration negativeCooldownTtl
    ) {
        this(
            meterRegistry,
            producerWaitTimeout,
            new CacheSettings(MAX_ENTRIES, freshTtl, staleTtl, negativeCooldownTtl)
        );
    }

    private FlowFilterQueryCacheService(
        MeterRegistry meterRegistry,
        Duration producerWaitTimeout,
        CacheSettings cacheSettings
    ) {
        CacheSettings normalizedSettings = cacheSettings.normalized();
        this.responseCache = Caffeine.newBuilder()
            .maximumSize(normalizedSettings.maxEntries())
            .expireAfterWrite(normalizedSettings.freshTtl())
            .build();
        this.staleResponseCache = Caffeine.newBuilder()
            .maximumSize(normalizedSettings.maxEntries())
            .expireAfterWrite(normalizedSettings.staleTtl())
            .build();
        this.negativeCooldownCache = Caffeine.newBuilder()
            .maximumSize(normalizedSettings.maxEntries())
            .expireAfterWrite(normalizedSettings.negativeCooldownTtl())
            .build();
        this.meterRegistry = meterRegistry;
        this.producerWaitTimeout = producerWaitTimeout == null
            ? DEFAULT_PRODUCER_WAIT_TIMEOUT
            : producerWaitTimeout;
        this.negativeCooldownRetryAfterSeconds = Math.max(
            1,
            (int) Math.ceil(normalizedSettings.negativeCooldownTtl().toMillis() / 1000.0)
        );
    }

    private static CacheSettings cacheSettings(OptionTradesProperties optionTradesProperties) {
        if (optionTradesProperties == null || optionTradesProperties.getCache() == null) {
            return DEFAULT_CACHE_SETTINGS;
        }

        OptionTradesProperties.Cache cache = optionTradesProperties.getCache();
        return new CacheSettings(
            cache.getMaxEntries(),
            cache.getFreshTtl(),
            cache.getStaleTtl(),
            cache.getNegativeCooldownTtl()
        );
    }

    public <T> T getOrLoad(
        FlowFilterQueryCacheKey key,
        boolean cacheResult,
        Class<T> responseType,
        Supplier<T> producer
    ) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(responseType, "responseType must not be null");
        Objects.requireNonNull(producer, "producer must not be null");

        CacheLookup<T> cacheLookup = tryServeCachedOrCooldown(key, cacheResult, responseType);
        if (cacheLookup.handled()) {
            return cacheLookup.value();
        }

        return loadFromProducer(key, cacheResult, responseType, producer);
    }

    private <T> CacheLookup<T> tryServeCachedOrCooldown(
        FlowFilterQueryCacheKey key,
        boolean cacheResult,
        Class<T> responseType
    ) {
        if (cacheResult) {
            Object cached = responseCache.getIfPresent(key);
            if (cached != null) {
                recordRequest(key, "hit");
                recordVisibleResponse(key, "cache_hit", OUTCOME_SUCCESS);
                return CacheLookup.handled(responseType.cast(cached));
            }
            recordRequest(key, "miss");

            FlowFilterNegativeCooldown cooldown = negativeCooldownCache.getIfPresent(key);
            if (cooldown != null) {
                Object stale = staleResponseCache.getIfPresent(key);
                if (stale != null) {
                    recordRequest(key, "negative_cooldown_stale");
                    recordVisibleResponse(key, ROLE_STALE, cooldown.cause());
                    return CacheLookup.handled(responseType.cast(stale));
                }
                recordRequest(key, OUTCOME_NEGATIVE_COOLDOWN);
                recordVisibleResponse(key, OUTCOME_NEGATIVE_COOLDOWN, cooldown.cause());
                throw new FlowFilterNegativeCooldownException(
                    "Flow query key is cooling down after a recent producer timeout",
                    cooldown.cause(),
                    negativeCooldownRetryAfterSeconds
                );
            }
        } else {
            recordRequest(key, "bypass");
        }

        return CacheLookup.miss();
    }

    private <T> T loadFromProducer(
        FlowFilterQueryCacheKey key,
        boolean cacheResult,
        Class<T> responseType,
        Supplier<T> producer
    ) {
        CompletableFuture<FlowFilterCacheResult> producerFuture = new CompletableFuture<>();
        CompletableFuture<FlowFilterCacheResult> existing = inFlight.putIfAbsent(key, producerFuture);
        if (existing != null) {
            recordRequest(key, "coalesced_waiter");
            return responseType.cast(awaitExistingProducer(key, existing));
        }

        long startedAtNanos = System.nanoTime();
        try {
            T produced = producer.get();
            if (cacheResult && produced != null) {
                responseCache.put(key, produced);
                staleResponseCache.put(key, produced);
                negativeCooldownCache.invalidate(key);
            }
            producerFuture.complete(FlowFilterCacheResult.success(produced));
            recordProducerDuration(key, OUTCOME_SUCCESS, startedAtNanos);
            recordVisibleResponse(key, ROLE_PRODUCER, OUTCOME_SUCCESS);
            return produced;
        } catch (Throwable throwable) {
            return handleProducerFailure(
                key,
                cacheResult,
                responseType,
                producerFuture,
                startedAtNanos,
                throwable
            );
        } finally {
            inFlight.remove(key, producerFuture);
        }
    }

    private <T> T handleProducerFailure(
        FlowFilterQueryCacheKey key,
        boolean cacheResult,
        Class<T> responseType,
        CompletableFuture<FlowFilterCacheResult> producerFuture,
        long startedAtNanos,
        Throwable throwable
    ) {
        String cause = timeoutCause(throwable);
        if (cacheResult && isTimeoutFailure(throwable)) {
            Object stale = staleResponseCache.getIfPresent(key);
            if (stale != null) {
                producerFuture.complete(FlowFilterCacheResult.stale(stale, cause));
                recordProducerDuration(key, OUTCOME_STALE_ON_ERROR, startedAtNanos);
                recordVisibleResponse(key, ROLE_STALE, cause);
                return responseType.cast(stale);
            }
            negativeCooldownCache.put(key, new FlowFilterNegativeCooldown(cause));
        }
        producerFuture.completeExceptionally(throwable);
        recordProducerDuration(key, cause, startedAtNanos);
        recordVisibleResponse(key, ROLE_PRODUCER, cause);
        throwUnchecked(throwable);
        throw new IllegalStateException("Unreachable after throwUnchecked");
    }

    private Object awaitExistingProducer(
        FlowFilterQueryCacheKey key,
        CompletableFuture<FlowFilterCacheResult> existing
    ) {
        long startedAtNanos = System.nanoTime();
        try {
            FlowFilterCacheResult result = existing.get(producerWaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            recordProducerDuration(key, "waiter_success", startedAtNanos);
            if (result.stale()) {
                recordVisibleResponse(key, ROLE_STALE, result.cause());
            } else {
                recordVisibleResponse(key, ROLE_WAITER, OUTCOME_SUCCESS);
            }
            return result.value();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordProducerDuration(key, "waiter_interrupted", startedAtNanos);
            recordVisibleResponse(key, ROLE_WAITER, OUTCOME_PRODUCER_ERROR);
            throw new IllegalStateException("Interrupted while waiting for Flow query producer", exception);
        } catch (TimeoutException exception) {
            recordProducerDuration(key, "waiter_timeout", startedAtNanos);
            recordVisibleResponse(key, ROLE_WAITER, "wait_timeout");
            throw new FlowFilterQueryWaitTimeoutException("Timed out waiting for Flow query producer", exception);
        } catch (ExecutionException exception) {
            recordProducerDuration(key, "waiter_error", startedAtNanos);
            Throwable cause = exception.getCause();
            if (isTimeoutFailure(cause)) {
                Object stale = staleResponseCache.getIfPresent(key);
                if (stale != null) {
                    recordRequest(key, "waiter_stale_on_error");
                    recordVisibleResponse(key, ROLE_STALE, timeoutCause(cause));
                    return stale;
                }
            }
            recordVisibleResponse(key, ROLE_WAITER, timeoutCause(cause));
            throw new FlowFilterProducerFailureException(
                "Flow query producer failed before waiter completed",
                cause,
                isTimeoutFailure(cause)
            );
        }
    }

    private void recordRequest(FlowFilterQueryCacheKey key, String outcome) {
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
            METRIC_REQUESTS,
            TAG_ENDPOINT, key.endpointTag(),
            TAG_OUTCOME, outcome,
            TAG_FILTER_SHAPE, key.filterShape(),
            TAG_COUNT_MODE, key.countMode().name().toLowerCase(Locale.ROOT)
        ).increment();
    }

    private void recordProducerDuration(
        FlowFilterQueryCacheKey key,
        String outcome,
        long startedAtNanos
    ) {
        if (meterRegistry == null) {
            return;
        }

        Timer.builder(METRIC_PRODUCER_DURATION)
            .description("Flow filter cache producer or waiter duration")
            .publishPercentileHistogram()
            .tag(TAG_ENDPOINT, key.endpointTag())
            .tag(TAG_OUTCOME, outcome)
            .tag(TAG_FILTER_SHAPE, key.filterShape())
            .tag(TAG_COUNT_MODE, key.countMode().name().toLowerCase(Locale.ROOT))
            .register(meterRegistry)
            .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }

    private void recordVisibleResponse(
        FlowFilterQueryCacheKey key,
        String role,
        String cause
    ) {
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
            METRIC_VISIBLE_RESPONSES,
            TAG_ENDPOINT, key.endpointTag(),
            TAG_ROLE, role,
            TAG_CAUSE, cause,
            TAG_FILTER_SHAPE, key.filterShape(),
            TAG_COUNT_MODE, key.countMode().name().toLowerCase(Locale.ROOT)
        ).increment();

        if (ROLE_STALE.equals(role)) {
            meterRegistry.counter(
                METRIC_STALE_SERVED,
                TAG_ENDPOINT, key.endpointTag(),
                TAG_CAUSE, cause,
                TAG_FILTER_SHAPE, key.filterShape(),
                TAG_COUNT_MODE, key.countMode().name().toLowerCase(Locale.ROOT)
            ).increment();
        }

        if (OUTCOME_NEGATIVE_COOLDOWN.equals(role)) {
            meterRegistry.counter(
                METRIC_NEGATIVE_COOLDOWN_RESPONSES,
                TAG_ENDPOINT, key.endpointTag(),
                TAG_CAUSE, cause,
                TAG_FILTER_SHAPE, key.filterShape(),
                TAG_COUNT_MODE, key.countMode().name().toLowerCase(Locale.ROOT)
            ).increment();
        }
    }

    private static String timeoutCause(Throwable throwable) {
        return isTimeoutFailure(throwable) ? "sql_timeout" : OUTCOME_PRODUCER_ERROR;
    }

    private static boolean isTimeoutFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof QueryTimeoutException || current instanceof TransactionTimedOutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void throwUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Flow query producer failed", throwable);
    }

    private record CacheSettings(
        int maxEntries,
        Duration freshTtl,
        Duration staleTtl,
        Duration negativeCooldownTtl
    ) {
        CacheSettings normalized() {
            return new CacheSettings(
                maxEntries > 0 ? maxEntries : MAX_ENTRIES,
                positiveDuration(freshTtl, CACHE_TTL),
                positiveDuration(staleTtl, STALE_TTL),
                positiveDuration(negativeCooldownTtl, NEGATIVE_COOLDOWN_TTL)
            );
        }
    }

    private record FlowFilterCacheResult(Object value, boolean stale, String cause) {
        static FlowFilterCacheResult success(Object value) {
            return new FlowFilterCacheResult(value, false, OUTCOME_SUCCESS);
        }

        static FlowFilterCacheResult stale(Object value, String cause) {
            return new FlowFilterCacheResult(value, true, cause);
        }
    }

    private record FlowFilterNegativeCooldown(String cause) {
    }

    private record CacheLookup<T>(boolean handled, T value) {
        static <T> CacheLookup<T> handled(T value) {
            return new CacheLookup<>(true, value);
        }

        static <T> CacheLookup<T> miss() {
            return new CacheLookup<>(false, null);
        }
    }

    private static Duration positiveDuration(Duration candidate, Duration fallback) {
        if (candidate == null || candidate.isZero() || candidate.isNegative()) {
            return fallback;
        }
        return candidate;
    }

    public record FlowFilterQueryCacheKey(
        String endpoint,
        LocalDate resolvedDay,
        String resolvedDaySource,
        String filterHash,
        String filterShape,
        OptionTradeCountMode countMode,
        int page,
        int pageSize,
        String sort
    ) {
        String endpointTag() {
            return endpoint != null && endpoint.endsWith("/aggregates") ? "aggregate" : "raw";
        }
    }

    public static class FlowFilterProducerFailureException extends RuntimeException {
        private final boolean timeoutFailure;

        public FlowFilterProducerFailureException(
            String message,
            Throwable cause,
            boolean timeoutFailure
        ) {
            super(message, cause);
            this.timeoutFailure = timeoutFailure;
        }

        public boolean timeoutFailure() {
            return timeoutFailure;
        }
    }

    public static class FlowFilterNegativeCooldownException extends RuntimeException {
        private final String causeTag;
        private final int retryAfterSeconds;

        public FlowFilterNegativeCooldownException(
            String message,
            String causeTag,
            int retryAfterSeconds
        ) {
            super(message);
            this.causeTag = causeTag;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public String causeTag() {
            return causeTag;
        }

        public int retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static class FlowFilterQueryWaitTimeoutException extends RuntimeException {
        public FlowFilterQueryWaitTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
