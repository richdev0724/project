package com.marketmono.controller;

import com.marketmono.service.OptionTradeMarketDayResolver;
import com.marketmono.exception.FlowFilterBusyException;
import com.marketmono.service.FlowFilterLoadGuard;
import com.marketmono.service.FlowFilterQueryCacheService;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.util.DateUtils;
import com.marketmono.service.FlowFilterDescriptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.TransactionTimedOutException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

final class FlowFilterControllerSupport {

    static final String ATTR_DURATION_MS = "durationMs";
    static final String ATTR_OUTCOME = "outcome";
    static final String ATTR_RESPONSE_ROWS = "responseRows";
    static final String ATTR_ENDPOINT = "endpoint";
    static final String ATTR_FILTER_SHAPE = "filterShape";
    static final String ATTR_EFFECTIVE_COUNT_MODE = "effectiveCountMode";
    static final String ATTR_NODE_ID = "nodeId";
    static final String ATTR_PAGE_SIZE = "pageSize";
    static final String ATTR_SORT_BY = "sortBy";
    static final String ATTR_REQUEST_ID = "requestId";
    static final String ATTR_RESOLVED_DAY_SOURCE = "resolvedDaySource";
    static final String ATTR_RESOLVED_DAY = "resolvedDay";
    static final String ATTR_FILTER_HASH = "filterHash";
    static final String ATTR_PAGE = "page";
    static final String ATTR_SIDE_FILTER_PRESENT = "sideFilterPresent";
    static final String ATTR_CANONICAL_RAW_DAY = "canonicalRawDay";
    static final String ATTR_AGGREGATE_MAX_DAY = "aggregateMaxDay";
    static final String ATTR_VOLUME_GT_OI = "volumeGtOi";
    static final String ATTR_SIZE_GT_OI = "sizeGtOi";
    static final String ATTR_EXCLUDE_DEEP_ITM = "excludeDeepItm";
    static final String ATTR_OTM_ONLY = "otmOnly";
    static final String ATTR_ITM_ONLY = "itmOnly";

    static final String OUTCOME_SHORT_CIRCUIT = "short_circuit";
    static final String OUTCOME_NEGATIVE_COOLDOWN = "negative_cooldown";
    static final String OUTCOME_TIMEOUT = "timeout";
    static final String OUTCOME_ERROR = "error";
    static final String OUTCOME_SUCCESS = "success";
    static final String OUTCOME_BUSY = "busy";

    static final String TAG_ENDPOINT = ATTR_ENDPOINT;
    static final String TAG_COUNT_MODE = "count_mode";
    static final String TAG_OUTCOME = ATTR_OUTCOME;
    static final String TAG_FILTER_SHAPE = "filter_shape";
    static final String TAG_PAGE_SIZE = "page_size";
    static final String TAG_NODE = "node";
    static final String TAG_ROLE = "role";
    static final String TAG_CAUSE = "cause";
    static final String UNKNOWN_TAG = "unknown";
    static final String SORT_NET_PREMIUM = "net_premium";
    static final String SORT_PREMIUM = "premium";
    static final String SORT_SIZE = "size";

    static final String METRIC_FLOW_QUERY_DURATION = "flow.filters.query.duration";
    static final String METRIC_FLOW_QUERY_REQUESTS = "flow.filters.query.requests";
    static final String METRIC_VISIBLE_RESPONSES = "flow.filters.query.cache.visible.responses";
    static final String METRIC_FLOW_RESPONSE_ROWS = "flow.filters.query.response.rows";
    static final String METRIC_FLOW_TIMEOUTS = "flow.filters.query.timeouts";
    static final String METRIC_FLOW_BUSY = "flow.filters.query.busy";
    static final String BODY_MESSAGE = "message";
    static final String BODY_CODE = "code";
    static final String QUERY_TIMEOUT_CODE = "QUERY_TIMEOUT";
    static final String BUSY_CODE = "EXPENSIVE_FILTER_BUSY";

    private FlowFilterControllerSupport() {
    }

    static ResolvedFlowWindow resolveFlowWindow(
        Instant since,
        Instant until,
        Supplier<Optional<OptionTradeMarketDayResolver.ResolvedMarketDay>> defaultDaySupplier
    ) {
        boolean explicitDateRange = since != null || until != null;
        OptionTradeMarketDayResolver.ResolvedMarketDay defaultDay = explicitDateRange
            ? null
            : defaultDaySupplier.get().orElse(null);

        Instant effectiveSince = since;
        if (effectiveSince == null && defaultDay != null) {
            effectiveSince = defaultDay.since();
        }
        if (effectiveSince == null && !explicitDateRange) {
            effectiveSince = DateUtils.startOfCurrentEasternDay();
        }

        Instant effectiveUntil = until;
        if (effectiveUntil == null && defaultDay != null) {
            effectiveUntil = defaultDay.until();
        }

        return new ResolvedFlowWindow(effectiveSince, effectiveUntil, defaultDay, explicitDateRange);
    }

    static void recordFlowFilterQueryMetric(
        MeterRegistry meterRegistry,
        Map<String, Object> attribution,
        String outcome,
        long durationMs,
        String endpoint
    ) {
        if (meterRegistry == null) {
            return;
        }

        Timer.builder(METRIC_FLOW_QUERY_DURATION)
            .description("Flow page filter query duration")
            .publishPercentileHistogram()
            .tag(TAG_ENDPOINT, endpoint)
            .tag(TAG_COUNT_MODE, enumTag(attribution.get(ATTR_EFFECTIVE_COUNT_MODE)))
            .tag(TAG_OUTCOME, outcome)
            .tag(TAG_NODE, safeTag(attribution.get(ATTR_NODE_ID)))
            .tag(TAG_FILTER_SHAPE, safeTag(attribution.get(ATTR_FILTER_SHAPE)))
            .tag(TAG_PAGE_SIZE, pageSizeBucket(attribution.get(ATTR_PAGE_SIZE)))
            .tag("sort", sortTag(attribution.get(ATTR_SORT_BY)))
            .tag("ticker", booleanTag(attribution.get("tickerPresent")))
            .tag(SORT_PREMIUM, booleanTag(
                Boolean.TRUE.equals(attribution.get("hasMinPremium"))
                    || Boolean.TRUE.equals(attribution.get("hasMaxPremium"))
            ))
            .tag("volume", booleanTag(
                Boolean.TRUE.equals(attribution.get("hasMinVolume"))
                    || Boolean.TRUE.equals(attribution.get("hasMaxVolume"))
            ))
            .tag("leg", legTag(attribution.get("singleleg"), attribution.get("multileg")))
            .tag("side", booleanTag(attribution.get(ATTR_SIDE_FILTER_PRESENT)))
            .tag("oi", booleanTag(
                Boolean.TRUE.equals(attribution.get(ATTR_VOLUME_GT_OI))
                    || Boolean.TRUE.equals(attribution.get(ATTR_SIZE_GT_OI))
            ))
            .tag("moneyness", booleanTag(
                Boolean.TRUE.equals(attribution.get(ATTR_EXCLUDE_DEEP_ITM))
                    || Boolean.TRUE.equals(attribution.get(ATTR_OTM_ONLY))
                    || Boolean.TRUE.equals(attribution.get(ATTR_ITM_ONLY))
            ))
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));

        meterRegistry.counter(
            METRIC_FLOW_QUERY_REQUESTS,
            TAG_ENDPOINT, endpoint,
            TAG_COUNT_MODE, enumTag(attribution.get(ATTR_EFFECTIVE_COUNT_MODE)),
            TAG_OUTCOME, outcome,
            TAG_FILTER_SHAPE, safeTag(attribution.get(ATTR_FILTER_SHAPE)),
            TAG_PAGE_SIZE, pageSizeBucket(attribution.get(ATTR_PAGE_SIZE))
        ).increment();

        if (OUTCOME_SHORT_CIRCUIT.equals(outcome)) {
            meterRegistry.counter(
                METRIC_VISIBLE_RESPONSES,
                TAG_ENDPOINT, endpoint,
                TAG_ROLE, OUTCOME_SHORT_CIRCUIT,
                TAG_CAUSE, OUTCOME_SUCCESS,
                TAG_FILTER_SHAPE, safeTag(attribution.get(ATTR_FILTER_SHAPE)),
                TAG_COUNT_MODE, enumTag(attribution.get(ATTR_EFFECTIVE_COUNT_MODE))
            ).increment();
        }

        recordFlowFilterResponseRows(meterRegistry, attribution, outcome, endpoint);

        if (OUTCOME_TIMEOUT.equals(outcome)) {
            meterRegistry.counter(
                METRIC_FLOW_TIMEOUTS,
                TAG_ENDPOINT, endpoint,
                TAG_FILTER_SHAPE, safeTag(attribution.get(ATTR_FILTER_SHAPE)),
                TAG_NODE, safeTag(attribution.get(ATTR_NODE_ID))
            ).increment();
        }

        if (OUTCOME_BUSY.equals(outcome) || OUTCOME_NEGATIVE_COOLDOWN.equals(outcome)) {
            meterRegistry.counter(
                METRIC_FLOW_BUSY,
                TAG_ENDPOINT, endpoint,
                TAG_FILTER_SHAPE, safeTag(attribution.get(ATTR_FILTER_SHAPE)),
                TAG_NODE, safeTag(attribution.get(ATTR_NODE_ID))
            ).increment();
        }
    }

    static <T> T getOrLoadFlowFilterResult(
        FlowFilterQueryCacheService cacheService,
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey cacheKey,
        boolean cacheResult,
        Class<T> responseType,
        Supplier<T> loader,
        FlowFilterOperation operation
    ) {
        try {
            return cacheService.getOrLoad(cacheKey, cacheResult, responseType, loader);
        } catch (FlowFilterQueryCacheService.FlowFilterNegativeCooldownException exception) {
            long durationMs = elapsedMillis(operation.startedAtNanos());
            operation.attribution().put(ATTR_DURATION_MS, durationMs);
            operation.attribution().put(ATTR_OUTCOME, OUTCOME_NEGATIVE_COOLDOWN);
            recordFlowFilterQueryMetric(
                operation.meterRegistry(),
                operation.attribution(),
                OUTCOME_NEGATIVE_COOLDOWN,
                durationMs,
                operation.endpoint()
            );
            operation.log().warn(
                "[PERF] {} query is in negative cooldown: {}",
                operation.logSubject(),
                operation.attribution(),
                exception
            );
            throw new FlowFilterBusyException(
                "Flow filter is cooling down after a recent timeout. Retry shortly.",
                operation.attribution(),
                exception.retryAfterSeconds()
            );
        } catch (FlowFilterQueryCacheService.FlowFilterProducerFailureException exception) {
            long durationMs = elapsedMillis(operation.startedAtNanos());
            String outcome = exception.timeoutFailure() ? OUTCOME_TIMEOUT : OUTCOME_ERROR;
            operation.attribution().put(ATTR_DURATION_MS, durationMs);
            operation.attribution().put(ATTR_OUTCOME, outcome);
            recordFlowFilterQueryMetric(
                operation.meterRegistry(),
                operation.attribution(),
                outcome,
                durationMs,
                operation.endpoint()
            );
            if (exception.timeoutFailure()) {
                operation.log().warn(
                    "[PERF] {} waiter inherited producer timeout: {}",
                    operation.logSubject(),
                    operation.attribution(),
                    exception
                );
                throw new FlowFilterQueryTimeoutException(
                    operation.timeoutMessage(),
                    operation.attribution(),
                    exception
                );
            }
            operation.log().warn(
                "[PERF] {} waiter inherited producer error: {}",
                operation.logSubject(),
                operation.attribution(),
                exception
            );
            throw exception;
        } catch (FlowFilterQueryCacheService.FlowFilterQueryWaitTimeoutException exception) {
            long durationMs = elapsedMillis(operation.startedAtNanos());
            operation.attribution().put(ATTR_DURATION_MS, durationMs);
            operation.attribution().put(ATTR_OUTCOME, OUTCOME_TIMEOUT);
            recordFlowFilterQueryMetric(
                operation.meterRegistry(),
                operation.attribution(),
                OUTCOME_TIMEOUT,
                durationMs,
                operation.endpoint()
            );
            operation.log().warn(
                "[PERF] {} coalesced query timed out: {}",
                operation.logSubject(),
                operation.attribution(),
                exception
            );
            throw new FlowFilterQueryTimeoutException(operation.timeoutMessage(), operation.attribution(), exception);
        }
    }

    static <T> T runGuardedFlowFilterQuery(
        FlowFilterLoadGuard loadGuard,
        Supplier<T> query,
        ToIntFunction<T> rowCounter,
        FlowFilterOperation operation
    ) {
        FlowFilterLoadGuard.GuardPermit guardPermit = loadGuard.tryAcquire(
            String.valueOf(operation.attribution().get(ATTR_ENDPOINT)),
            String.valueOf(operation.attribution().get(ATTR_FILTER_SHAPE))
        );
        if (guardPermit.isBusy()) {
            long durationMs = elapsedMillis(operation.startedAtNanos());
            operation.attribution().put(ATTR_DURATION_MS, durationMs);
            operation.attribution().put(ATTR_OUTCOME, OUTCOME_BUSY);
            recordFlowFilterQueryMetric(
                operation.meterRegistry(),
                operation.attribution(),
                OUTCOME_BUSY,
                durationMs,
                operation.endpoint()
            );
            operation.log().warn("[PERF] {} expensive query busy: {}", operation.logSubject(), operation.attribution());
            throw new FlowFilterBusyException(
                "Expensive Flow filter is busy. Narrow filters or retry shortly.",
                operation.attribution(),
                guardPermit.retryAfterSeconds()
            );
        }

        T result;
        try (guardPermit) {
            result = query.get();
        } catch (QueryTimeoutException | TransactionTimedOutException exception) {
            long durationMs = elapsedMillis(operation.startedAtNanos());
            operation.attribution().put(ATTR_DURATION_MS, durationMs);
            operation.attribution().put(ATTR_OUTCOME, OUTCOME_TIMEOUT);
            recordFlowFilterQueryMetric(
                operation.meterRegistry(),
                operation.attribution(),
                OUTCOME_TIMEOUT,
                durationMs,
                operation.endpoint()
            );
            operation.log().warn("[PERF] {} query timed out: {}", operation.logSubject(), operation.attribution(), exception);
            throw new FlowFilterQueryTimeoutException(operation.timeoutMessage(), operation.attribution(), exception);
        } catch (RuntimeException exception) {
            long durationMs = elapsedMillis(operation.startedAtNanos());
            operation.attribution().put(ATTR_DURATION_MS, durationMs);
            operation.attribution().put(ATTR_OUTCOME, OUTCOME_ERROR);
            recordFlowFilterQueryMetric(
                operation.meterRegistry(),
                operation.attribution(),
                OUTCOME_ERROR,
                durationMs,
                operation.endpoint()
            );
            operation.log().warn("[PERF] {} query failed: {}", operation.logSubject(), operation.attribution(), exception);
            throw exception;
        }

        long durationMs = elapsedMillis(operation.startedAtNanos());
        operation.attribution().put(ATTR_DURATION_MS, durationMs);
        operation.attribution().put(ATTR_OUTCOME, OUTCOME_SUCCESS);
        operation.attribution().put(ATTR_RESPONSE_ROWS, rowCounter.applyAsInt(result));
        recordFlowFilterQueryMetric(
            operation.meterRegistry(),
            operation.attribution(),
            OUTCOME_SUCCESS,
            durationMs,
            operation.endpoint()
        );
        if (durationMs >= 4000L) {
            operation.log().warn("[PERF] Slow {} query: {}", operation.logSubject(), operation.attribution());
        }
        return result;
    }

    static void addMarketDayAttribution(
        Map<String, Object> attribution,
        OptionTradeMarketDayResolver.ResolvedMarketDay resolvedDefaultDay,
        boolean explicitDateRange
    ) {
        if (explicitDateRange) {
            attribution.put(ATTR_RESOLVED_DAY_SOURCE, "explicit");
            return;
        }

        if (resolvedDefaultDay == null) {
            attribution.put(ATTR_RESOLVED_DAY_SOURCE, "current_calendar_fallback");
            return;
        }

        attribution.put(ATTR_RESOLVED_DAY_SOURCE, resolvedDefaultDay.source());
        attribution.put(ATTR_RESOLVED_DAY, resolvedDefaultDay.day());
        attribution.put(ATTR_CANONICAL_RAW_DAY, resolvedDefaultDay.rawMaxDay());
        attribution.put(ATTR_AGGREGATE_MAX_DAY, resolvedDefaultDay.aggregateMaxDay());
    }

    static FlowFilterQueryCacheService.FlowFilterQueryCacheKey buildFlowFilterQueryCacheKey(
        Map<String, Object> attribution,
        OptionTradeCountMode effectiveCountMode
    ) {
        return new FlowFilterQueryCacheService.FlowFilterQueryCacheKey(
            String.valueOf(attribution.get(ATTR_ENDPOINT)),
            attribution.get(ATTR_RESOLVED_DAY) instanceof LocalDate resolvedDay ? resolvedDay : null,
            String.valueOf(attribution.get(ATTR_RESOLVED_DAY_SOURCE)),
            String.valueOf(attribution.get(ATTR_FILTER_HASH)),
            String.valueOf(attribution.get(ATTR_FILTER_SHAPE)),
            effectiveCountMode,
            attribution.get(ATTR_PAGE) instanceof Number page ? page.intValue() : 1,
            attribution.get(ATTR_PAGE_SIZE) instanceof Number pageSize ? pageSize.intValue() : 100,
            String.valueOf(attribution.get(ATTR_SORT_BY))
        );
    }

    static Map<String, Object> buildFlowFilterAttribution(
        FlowFilterAttributionContext context,
        FlowFilterDescriptor filter,
        boolean sideFilterPresent,
        String filterSummary,
        String filterShape
    ) {
        Map<String, Object> attribution = new LinkedHashMap<>();
        attribution.put(ATTR_REQUEST_ID, context.requestId());
        attribution.put(ATTR_NODE_ID, context.nodeId());
        attribution.put(ATTR_ENDPOINT, context.endpointPath());
        attribution.put(ATTR_PAGE, context.pageable().getPageNumber() + 1);
        attribution.put(ATTR_PAGE_SIZE, context.pageable().getPageSize());
        attribution.put(ATTR_SORT_BY, context.sortBy());
        attribution.put("requestedCountMode", context.requestedCountMode());
        attribution.put(ATTR_EFFECTIVE_COUNT_MODE, context.effectiveCountMode());
        attribution.put("tickerPresent", filter.tickers() != null && !filter.tickers().isEmpty());
        attribution.put("tickerCount", filter.tickers() == null ? 0 : filter.tickers().size());
        attribution.put("ignoreTickerCount", filter.ignoreTickers() == null ? 0 : filter.ignoreTickers().size());
        attribution.put("hasMinPremium", filter.minPremium() != null);
        attribution.put("hasMaxPremium", filter.maxPremium() != null);
        attribution.put("hasMinVolume", filter.minVolume() != null);
        attribution.put("hasMaxVolume", filter.maxVolume() != null);
        attribution.put("singleleg", filter.hasSingleleg());
        attribution.put("multileg", filter.hasMultileg());
        attribution.put(ATTR_SIDE_FILTER_PRESENT, sideFilterPresent);
        attribution.put(ATTR_VOLUME_GT_OI, Boolean.TRUE.equals(filter.volumeGtOi()));
        attribution.put(ATTR_SIZE_GT_OI, Boolean.TRUE.equals(filter.sizeGtOi()));
        attribution.put(ATTR_EXCLUDE_DEEP_ITM, Boolean.TRUE.equals(filter.excludeDeepItm()));
        attribution.put(ATTR_OTM_ONLY, Boolean.TRUE.equals(filter.otmOnly()));
        attribution.put(ATTR_ITM_ONLY, Boolean.TRUE.equals(filter.itmOnly()));
        attribution.put("filterSummary", filterSummary);
        attribution.put(ATTR_FILTER_HASH, hashFilterSummary(filterSummary));
        attribution.put(ATTR_FILTER_SHAPE, filterShape);
        return attribution;
    }

    static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    static String classifyFilterShape(boolean hasTicker, boolean hasRange, boolean hasExpensive) {
        if (hasTicker && hasRange && hasExpensive) {
            return "ticker_range_expensive";
        }
        if (hasTicker && hasExpensive) {
            return "ticker_expensive";
        }
        if (hasRange && hasExpensive) {
            return "range_expensive";
        }
        if (hasExpensive) {
            return "expensive";
        }
        if (hasTicker && hasRange) {
            return "ticker_range";
        }
        if (hasTicker) {
            return "ticker";
        }
        if (hasRange) {
            return "range";
        }
        return "default";
    }

    static boolean isExcludedSideBucket(Boolean value) {
        return Boolean.FALSE.equals(value);
    }

    static String summarizeFilter(FlowFilterDescriptor filter, Boolean mid) {
        List<String> parts = new ArrayList<>();
        addListPart(parts, "tickers", filter.tickers());
        addListPart(parts, "ignoreTickers", filter.ignoreTickers());
        addPart(parts, "since", filter.since());
        addPart(parts, "until", filter.until());
        addListPart(parts, "expiration", filter.expirations());
        addPart(parts, "minExpiryDate", filter.minExpiryDate());
        addPart(parts, "maxExpiryDate", filter.maxExpiryDate());
        addPart(parts, "minPremium", filter.minPremium());
        addPart(parts, "maxPremium", filter.maxPremium());
        addPart(parts, "minSize", filter.minSize());
        addPart(parts, "maxSize", filter.maxSize());
        addPart(parts, "minVolume", filter.minVolume());
        addPart(parts, "maxVolume", filter.maxVolume());
        addPart(parts, "calls", filter.calls());
        addPart(parts, "puts", filter.puts());
        addPart(parts, "sweepOnly", filter.sweepOnly());
        addPart(parts, "floorOnly", filter.floorOnly());
        addPart(parts, "hasSweep", filter.hasSweep());
        addPart(parts, "hasFloor", filter.hasFloor());
        addPart(parts, "hasSingleleg", filter.hasSingleleg());
        addPart(parts, "hasMultileg", filter.hasMultileg());
        addPart(parts, "side", filter.side());
        addPart(parts, "cross", filter.cross());
        addPart(parts, "regular", filter.regular());
        addPart(parts, "bid", filter.bid());
        addPart(parts, "mid", mid);
        addPart(parts, "ask", filter.ask());
        addPart(parts, "noSide", filter.noSide());
        addPart(parts, "aboveAsk", filter.aboveAsk());
        addPart(parts, "belowBid", filter.belowBid());
        addPart(parts, ATTR_VOLUME_GT_OI, filter.volumeGtOi());
        addPart(parts, ATTR_SIZE_GT_OI, filter.sizeGtOi());
        addPart(parts, ATTR_EXCLUDE_DEEP_ITM, filter.excludeDeepItm());
        addPart(parts, ATTR_OTM_ONLY, filter.otmOnly());
        addPart(parts, ATTR_ITM_ONLY, filter.itmOnly());
        addPart(parts, "hideExpired", filter.hideExpired());
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }

    static void addListPart(List<String> parts, String name, List<?> values) {
        if (values != null && !values.isEmpty()) {
            parts.add(name + "=" + String.join(",", values.stream()
                .map(String::valueOf)
                .sorted(Comparator.naturalOrder())
                .toList()));
        }
    }

    static void addPart(List<String> parts, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value instanceof BigDecimal decimal) {
            parts.add(name + "=" + decimal.stripTrailingZeros().toPlainString());
            return;
        }
        parts.add(name + "=" + value);
    }

    static String hashFilterSummary(String filterSummary) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(filterSummary.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    static Map<String, Object> timeoutBody(String message, Map<String, Object> attribution) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(BODY_MESSAGE, message);
        body.put(BODY_CODE, QUERY_TIMEOUT_CODE);
        body.put(ATTR_REQUEST_ID, attribution.get(ATTR_REQUEST_ID));
        body.put(ATTR_ENDPOINT, attribution.get(ATTR_ENDPOINT));
        addMarketDayBodyFields(body, attribution);
        return body;
    }

    static Map<String, Object> busyBody(Map<String, Object> attribution, int retryAfterSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(BODY_MESSAGE, "Expensive Flow filter is busy. Narrow filters or retry shortly.");
        body.put(BODY_CODE, BUSY_CODE);
        body.put(ATTR_REQUEST_ID, attribution.get(ATTR_REQUEST_ID));
        body.put(ATTR_ENDPOINT, attribution.get(ATTR_ENDPOINT));
        body.put("retryAfterSeconds", retryAfterSeconds);
        addMarketDayBodyFields(body, attribution);
        return body;
    }

    static final class FlowFilterQueryTimeoutException extends RuntimeException {
        private final transient Map<String, Object> attribution;

        FlowFilterQueryTimeoutException(
            String message,
            Map<String, Object> attribution,
            RuntimeException cause
        ) {
            super(message, cause);
            this.attribution = new LinkedHashMap<>(attribution);
        }

        Map<String, Object> attribution() {
            return attribution;
        }
    }

    private static void recordFlowFilterResponseRows(
        MeterRegistry meterRegistry,
        Map<String, Object> attribution,
        String outcome,
        String endpoint
    ) {
        if (!OUTCOME_SUCCESS.equals(outcome)) {
            return;
        }

        Object responseRows = attribution.get(ATTR_RESPONSE_ROWS);
        if (!(responseRows instanceof Number number)) {
            return;
        }

        meterRegistry.summary(
            METRIC_FLOW_RESPONSE_ROWS,
            TAG_ENDPOINT, endpoint,
            TAG_COUNT_MODE, enumTag(attribution.get(ATTR_EFFECTIVE_COUNT_MODE)),
            TAG_FILTER_SHAPE, safeTag(attribution.get(ATTR_FILTER_SHAPE)),
            TAG_PAGE_SIZE, pageSizeBucket(attribution.get(ATTR_PAGE_SIZE))
        ).record(number.doubleValue());
    }

    private static String enumTag(Object value) {
        return value == null ? UNKNOWN_TAG : value.toString().toLowerCase();
    }

    private static String safeTag(Object value) {
        if (value == null) {
            return UNKNOWN_TAG;
        }

        String text = value.toString().trim();
        return text.isEmpty() ? UNKNOWN_TAG : text;
    }

    private static String sortTag(Object value) {
        String text = safeTag(value).toLowerCase();
        return switch (text) {
            case SORT_NET_PREMIUM, SORT_PREMIUM -> SORT_PREMIUM;
            case SORT_SIZE -> SORT_SIZE;
            default -> "time";
        };
    }

    private static String pageSizeBucket(Object value) {
        if (!(value instanceof Number number)) {
            return UNKNOWN_TAG;
        }

        int pageSize = number.intValue();
        if (pageSize <= 50) {
            return "le_50";
        }
        if (pageSize <= 100) {
            return "le_100";
        }
        if (pageSize <= 250) {
            return "le_250";
        }
        if (pageSize <= 500) {
            return "le_500";
        }
        return "gt_500";
    }

    private static String booleanTag(Object value) {
        return Boolean.TRUE.equals(value) ? "yes" : "no";
    }

    private static String legTag(Object singleleg, Object multileg) {
        if (Boolean.TRUE.equals(singleleg)) {
            return "single";
        }
        if (Boolean.TRUE.equals(multileg)) {
            return "multi";
        }
        return "any";
    }

    private static void addMarketDayBodyFields(Map<String, Object> body, Map<String, Object> attribution) {
        copyIfPresent(body, attribution, ATTR_RESOLVED_DAY_SOURCE);
        copyIfPresent(body, attribution, ATTR_RESOLVED_DAY);
        copyIfPresent(body, attribution, ATTR_CANONICAL_RAW_DAY);
        copyIfPresent(body, attribution, ATTR_AGGREGATE_MAX_DAY);
    }

    private static void copyIfPresent(Map<String, Object> body, Map<String, Object> attribution, String key) {
        Object value = attribution.get(key);
        if (value != null) {
            body.put(key, value);
        }
    }

    record ResolvedFlowWindow(
        Instant since,
        Instant until,
        OptionTradeMarketDayResolver.ResolvedMarketDay defaultDay,
        boolean explicitDateRange
    ) {}

    record FlowFilterOperation(
        long startedAtNanos,
        Map<String, Object> attribution,
        String endpoint,
        MeterRegistry meterRegistry,
        Logger log,
        String logSubject,
        String timeoutMessage
    ) {}

    record FlowFilterAttributionContext(
        UUID requestId,
        String nodeId,
        String endpointPath,
        PageRequest pageable,
        String sortBy,
        OptionTradeCountMode requestedCountMode,
        OptionTradeCountMode effectiveCountMode
    ) {}
}
