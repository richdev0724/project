package com.marketmono.controller;

import com.marketmono.dto.OptionTradeAggregateDto;
import com.marketmono.dto.OptionTradeAggregatePageResponse;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeDto;
import com.marketmono.exception.FlowFilterBusyException;
import com.marketmono.service.FlowFilterLoadGuard;
import com.marketmono.service.FlowFilterQueryCacheService;
import com.marketmono.service.HotOptionTradeAggregateCacheService;
import com.marketmono.service.NodeIdentityService;
import com.marketmono.service.OptionTradeAggregateBroadcaster;
import com.marketmono.service.OptionTradeAggregateService;
import com.marketmono.service.OptionTradeAggregateService.OptionTradeAggregateFilter;
import com.marketmono.service.OptionTradeAggregateService.OptionTradeAggregateQueryResult;
import com.marketmono.service.OptionTradeMarketDayResolver;
import com.marketmono.util.DateUtils;
import com.marketmono.util.Pagination;
import com.marketmono.util.RequestParamUtils;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.marketmono.controller.FlowFilterControllerSupport.*;

@RestController
@RequestMapping("/api/v1/option-trades/aggregates")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class OptionTradeAggregatesController {
    private static final String ENDPOINT_TAG_AGGREGATE = "aggregate";
    private static final String QUERY_TIMEOUT_MESSAGE = "Option trade aggregate query timed out";
    private static final String SORT_LAST_EXECUTED_AT = "lastExecutedAt";
    private static final String SORT_AGGREGATE_ID = "aggregateId";
    private static final String SORT_TOTAL_PREMIUM = "totalPremium";
    private static final String SORT_TOTAL_SIZE = "totalSize";

    private final OptionTradeAggregateService aggregateService;
    private final OptionTradeAggregateBroadcaster aggregateBroadcaster;
    private final HotOptionTradeAggregateCacheService hotAggregateCacheService;
    private final FlowFilterLoadGuard flowFilterLoadGuard;
    private final NodeIdentityService nodeIdentityService;
    private final OptionTradeMarketDayResolver marketDayResolver;
    private final FlowFilterQueryCacheService flowFilterQueryCacheService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @GetMapping
    public ResponseEntity<OptionTradeAggregatePageResponse> getAggregates(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "100") int pageSize,
        @RequestParam(required = false) String tickers,
        @RequestParam(required = false) BigDecimal minPremium,
        @RequestParam(required = false) Boolean sweepOnly,
        @RequestParam(required = false) Boolean floorOnly,
        @RequestParam(required = false) String side,
        @RequestParam(required = false) Instant since,
        @RequestParam(required = false) Instant until,
        @RequestParam(required = false) String ignoreTickers,
        @RequestParam(required = false) BigDecimal maxPremium,
        @RequestParam(required = false) Long minSize,
        @RequestParam(required = false) Long maxSize,
        @RequestParam(required = false) Long minVolume,
        @RequestParam(required = false) Long maxVolume,
        @RequestParam(required = false) String expiration,
        @RequestParam(required = false) Integer minDte,
        @RequestParam(required = false) Integer maxDte,
        @RequestParam(required = false) Boolean calls,
        @RequestParam(required = false) Boolean puts,
        @RequestParam(required = false) Boolean sweeps,
        @RequestParam(required = false) Boolean floor,
        @RequestParam(required = false) Boolean hasSingleleg,
        @RequestParam(required = false) Boolean hasMultileg,
        @RequestParam(required = false) Boolean cross,
        @RequestParam(required = false) Boolean regular,
        @RequestParam(required = false) Boolean bid,
        @RequestParam(required = false) Boolean ask,
        @RequestParam(required = false) Boolean noSide,
        @RequestParam(required = false) Boolean aboveAsk,
        @RequestParam(required = false) Boolean belowBid,
        @RequestParam(required = false) Boolean volumeGtOi,
        @RequestParam(required = false) Boolean sizeGtOi,
        @RequestParam(required = false) Boolean excludeDeepItm,
        @RequestParam(required = false) Boolean otmOnly,
        @RequestParam(required = false) Boolean itmOnly,
        @RequestParam(required = false) Boolean hideExpired,
        @RequestParam(defaultValue = "sip_timestamp") String sortBy,
        @RequestParam(defaultValue = "slice") String countMode
    ) {
        PageRequest pageable = Pagination.cappedPageRequest(
            page,
            pageSize,
            500,
            buildSort(sortBy)
        );

        List<String> tickerList = RequestParamUtils.parseUpperCsv(tickers);
        List<String> ignoreTickerList = RequestParamUtils.parseUpperCsv(ignoreTickers);
        List<LocalDate> expiryList = RequestParamUtils.parseLocalDateCsv(expiration);

        ResolvedFlowWindow flowWindow = resolveFlowWindow(
            since,
            until,
            marketDayResolver::resolveAggregateDefaultDay
        );
        LocalDate todayEastern = DateUtils.getCurrentDateInEasternTime();
        LocalDate minExpiryDate = minDte != null ? todayEastern.plusDays(minDte) : null;
        LocalDate maxExpiryDate = maxDte != null ? todayEastern.plusDays(maxDte) : null;
        String normalizedSide = RequestParamUtils.normalizeSide(side);

        OptionTradeAggregateFilter filter = new OptionTradeAggregateFilter(
            tickerList,
            ignoreTickerList,
            flowWindow.since(),
            flowWindow.until(),
            expiryList,
            minExpiryDate,
            maxExpiryDate,
            minPremium,
            maxPremium,
            minSize,
            maxSize,
            minVolume,
            maxVolume,
            calls,
            puts,
            sweepOnly,
            floorOnly,
            sweeps != null && !sweeps ? Boolean.FALSE : null,
            floor != null && !floor ? Boolean.FALSE : null,
            hasSingleleg,
            hasMultileg,
            normalizedSide,
            cross,
            regular,
            bid,
            ask,
            noSide,
            aboveAsk,
            belowBid,
            volumeGtOi,
            sizeGtOi,
            excludeDeepItm,
            otmOnly,
            itmOnly,
            hideExpired
        );

        OptionTradeCountMode requestedCountMode;
        try {
            requestedCountMode = OptionTradeCountMode.fromQueryParam(countMode);
        } catch (IllegalArgumentException ex) {
            log.warn("Rejecting invalid countMode for /api/v1/option-trades/aggregates: {}", countMode);
            return ResponseEntity.badRequest().build();
        }
        OptionTradeCountMode effectiveCountMode = excludesAllAggregateSideBuckets(filter)
            ? requestedCountMode
            : resolveEffectiveCountMode(requestedCountMode, filter);

        UUID requestId = UUID.randomUUID();
        long startedAtNanos = System.nanoTime();
        Map<String, Object> attribution = buildAttribution(
            requestId,
            pageable,
            filter,
            sortBy,
            requestedCountMode,
            effectiveCountMode
        );
        addMarketDayAttribution(attribution, flowWindow.defaultDay(), flowWindow.explicitDateRange());

        if (excludesAllAggregateSideBuckets(filter)) {
            long durationMs = elapsedMillis(startedAtNanos);
            attribution.put(ATTR_DURATION_MS, durationMs);
            attribution.put(ATTR_OUTCOME, OUTCOME_SHORT_CIRCUIT);
            attribution.put(ATTR_RESPONSE_ROWS, 0);
            recordFlowFilterQueryMetric(attribution, OUTCOME_SHORT_CIRCUIT, durationMs);
            log.debug("[PERF] Option-trade aggregates side filter short-circuited to empty result: {}", attribution);
            return ResponseEntity.ok(emptyAggregateResponse(effectiveCountMode));
        }

        HotOptionTradeAggregateCacheService.HotAggregatePageKey hotCacheKey = buildHotAggregatePageKey(
            page,
            pageable.getPageSize(),
            minPremium,
            tickers,
            ignoreTickers,
            sweepOnly,
            floorOnly,
            side,
            since,
            until,
            maxPremium,
            minSize,
            maxSize,
            minVolume,
            maxVolume,
            expiration,
            minDte,
            maxDte,
            calls,
            puts,
            sweeps,
            floor,
            hasSingleleg,
            hasMultileg,
            cross,
            regular,
            bid,
            ask,
            noSide,
            aboveAsk,
            belowBid,
            volumeGtOi,
            sizeGtOi,
            excludeDeepItm,
            otmOnly,
            itmOnly,
            hideExpired,
            sortBy,
            flowWindow.defaultDay(),
            effectiveCountMode
        );

        if (hotCacheKey != null && effectiveCountMode == OptionTradeCountMode.SLICE) {
            OptionTradeAggregatePageResponse cached = hotAggregateCacheService.getIfPresent(hotCacheKey);
            if (cached != null) {
                log.debug("Serving hot aggregate page from cache: {}", hotCacheKey);
                return ResponseEntity.ok(cached);
            }
        }

        FlowFilterQueryCacheService.FlowFilterQueryCacheKey cacheKey =
            buildFlowFilterQueryCacheKey(attribution, effectiveCountMode);
        boolean cacheResult = effectiveCountMode == OptionTradeCountMode.SLICE;
        OptionTradeAggregatePageResponse response = getOrLoadFlowFilterResult(
            flowFilterQueryCacheService,
            cacheKey,
            cacheResult,
            OptionTradeAggregatePageResponse.class,
            () -> loadAggregates(startedAtNanos, attribution, filter, pageable, effectiveCountMode, hotCacheKey),
            new FlowFilterOperation(
                startedAtNanos,
                attribution,
                ENDPOINT_TAG_AGGREGATE,
                meterRegistryProvider.getIfAvailable(),
                log,
                "Option-trade aggregates",
                QUERY_TIMEOUT_MESSAGE
            )
        );

        return ResponseEntity.ok(response);
    }

    private static boolean excludesAllAggregateSideBuckets(OptionTradeAggregateFilter filter) {
        return Boolean.FALSE.equals(filter.bid())
            && Boolean.FALSE.equals(filter.ask())
            && Boolean.FALSE.equals(filter.noSide())
            && Boolean.FALSE.equals(filter.aboveAsk())
            && Boolean.FALSE.equals(filter.belowBid());
    }

    private static OptionTradeAggregatePageResponse emptyAggregateResponse(OptionTradeCountMode countMode) {
        if (countMode == OptionTradeCountMode.EXACT) {
            return new OptionTradeAggregatePageResponse(List.of(), 0L, false, true, OptionTradeCountMode.EXACT);
        }
        return new OptionTradeAggregatePageResponse(List.of(), null, false, false, OptionTradeCountMode.SLICE);
    }

    private OptionTradeAggregatePageResponse loadAggregates(
        long startedAtNanos,
        Map<String, Object> attribution,
        OptionTradeAggregateFilter filter,
        PageRequest pageable,
        OptionTradeCountMode effectiveCountMode,
        HotOptionTradeAggregateCacheService.HotAggregatePageKey hotCacheKey
    ) {
        OptionTradeAggregateQueryResult result = runGuardedFlowFilterQuery(
            flowFilterLoadGuard,
            () -> aggregateService.getAggregates(filter, pageable, effectiveCountMode),
            queryResult -> queryResult.results().size(),
            new FlowFilterOperation(
                startedAtNanos,
                attribution,
                ENDPOINT_TAG_AGGREGATE,
                meterRegistryProvider.getIfAvailable(),
                log,
                "option-trade aggregates",
                QUERY_TIMEOUT_MESSAGE
            )
        );

        OptionTradeAggregatePageResponse response = new OptionTradeAggregatePageResponse(
            List.copyOf(result.results()),
            result.totalCount(),
            result.hasNext(),
            result.totalCountExact(),
            result.countMode()
        );

        if (hotCacheKey != null && result.countMode() == OptionTradeCountMode.SLICE) {
            hotAggregateCacheService.put(hotCacheKey, response);
        }

        return response;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<OptionTradeAggregateDto>> getRecentAggregates(
        @RequestParam(defaultValue = "500") int limit
    ) {
        List<OptionTradeAggregateDto> recent = aggregateBroadcaster.getRecentAggregates(limit);
        if (!recent.isEmpty()) {
            return ResponseEntity.ok(aggregateService.hydrateVolumes(recent));
        }

        OptionTradeMarketDayResolver.ResolvedMarketDay resolvedDefaultDay =
            marketDayResolver.resolveAggregateDefaultDay().orElse(null);
        if (resolvedDefaultDay == null) {
            return ResponseEntity.ok(aggregateService.getRecentAggregates(limit));
        }

        log.warn(
            "[RECENT-FALLBACK] Aggregate DB fallback fired - buffer empty (local + Redis). limit={} resolvedDaySource={} resolvedDay={} canonicalRawDay={} aggregateMaxDay={}",
            limit,
            resolvedDefaultDay.source(),
            resolvedDefaultDay.day(),
            resolvedDefaultDay.rawMaxDay(),
            resolvedDefaultDay.aggregateMaxDay()
        );
        return ResponseEntity.ok(aggregateService.getRecentAggregates(
            limit,
            resolvedDefaultDay.since(),
            resolvedDefaultDay.until()
        ));
    }

    @GetMapping("/{aggregateId}/constituents")
    public ResponseEntity<List<OptionTradeDto>> getConstituents(
        @PathVariable UUID aggregateId
    ) {
        return ResponseEntity.ok(aggregateService.getConstituents(aggregateId));
    }

    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class, FlowFilterQueryTimeoutException.class})
    public ResponseEntity<Map<String, Object>> handleAggregateQueryTimeout(RuntimeException exception) {
        if (exception instanceof FlowFilterQueryTimeoutException timeoutException) {
            recordAggregateTimeoutMetric();
            return ResponseEntity.status(503).body(timeoutBody(
                timeoutException.getMessage(),
                timeoutException.attribution()
            ));
        }

        log.warn("[PERF] Option-trade aggregate query timed out: {}", exception.getMessage(), exception);
        recordAggregateTimeoutMetric();
        return ResponseEntity.status(503).body(Map.of(
            "message", QUERY_TIMEOUT_MESSAGE,
            "code", "QUERY_TIMEOUT"
        ));
    }

    @ExceptionHandler(FlowFilterBusyException.class)
    public ResponseEntity<Map<String, Object>> handleFlowFilterBusy(FlowFilterBusyException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
            .body(busyBody(exception.attribution(), exception.retryAfterSeconds()));
    }

    private void recordAggregateTimeoutMetric() {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter("option_trade_aggregates.query.timeouts").increment();
    }

    private void recordFlowFilterQueryMetric(
        Map<String, Object> attribution,
        String outcome,
        long durationMs
    ) {
        FlowFilterControllerSupport.recordFlowFilterQueryMetric(
            meterRegistryProvider.getIfAvailable(),
            attribution,
            outcome,
            durationMs,
            ENDPOINT_TAG_AGGREGATE
        );
    }

    private Map<String, Object> buildAttribution(
        UUID requestId,
        PageRequest pageable,
        OptionTradeAggregateFilter filter,
        String sortBy,
        OptionTradeCountMode requestedCountMode,
        OptionTradeCountMode effectiveCountMode
    ) {
        String filterSummary = summarizeFilter(filter, null);
        return buildFlowFilterAttribution(
            new FlowFilterAttributionContext(
                requestId,
                nodeIdentityService.getNodeId(),
                "/api/v1/option-trades/aggregates",
                pageable,
                sortBy,
                requestedCountMode,
                effectiveCountMode
            ),
            filter,
            isAggregateSideFilterPresent(filter),
            filterSummary,
            classifyFilterShape(filter)
        );
    }

    private OptionTradeCountMode resolveEffectiveCountMode(
        OptionTradeCountMode requestedCountMode,
        OptionTradeAggregateFilter filter
    ) {
        if (requestedCountMode == OptionTradeCountMode.EXACT && hasExpensiveAggregatePredicates(filter)) {
            log.info(
                "Using slice count mode for expensive option-trade aggregate filter set: tickerPresent={}, minVolume={}, sizeGtOi={}, volumeGtOi={}, otmOnly={}, itmOnly={}, excludeDeepItm={}",
                filter.tickers() != null && !filter.tickers().isEmpty(),
                filter.minVolume() != null,
                filter.sizeGtOi(),
                filter.volumeGtOi(),
                filter.otmOnly(),
                filter.itmOnly(),
                filter.excludeDeepItm()
            );
            return OptionTradeCountMode.SLICE;
        }

        return requestedCountMode;
    }

    private boolean hasExpensiveAggregatePredicates(OptionTradeAggregateFilter filter) {
        return filter.tickers() != null && !filter.tickers().isEmpty()
            || filter.minVolume() != null
            || filter.maxVolume() != null
            || Boolean.TRUE.equals(filter.sizeGtOi())
            || Boolean.TRUE.equals(filter.volumeGtOi())
            || Boolean.TRUE.equals(filter.otmOnly())
            || Boolean.TRUE.equals(filter.itmOnly())
            || Boolean.TRUE.equals(filter.excludeDeepItm())
            || isExcludedSideBucket(filter.bid())
            || isExcludedSideBucket(filter.ask())
            || isExcludedSideBucket(filter.noSide())
            || isExcludedSideBucket(filter.aboveAsk())
            || isExcludedSideBucket(filter.belowBid());
    }

    private boolean isAggregateSideFilterPresent(OptionTradeAggregateFilter filter) {
        return isExcludedSideBucket(filter.bid())
            || isExcludedSideBucket(filter.ask())
            || isExcludedSideBucket(filter.noSide())
            || isExcludedSideBucket(filter.aboveAsk())
            || isExcludedSideBucket(filter.belowBid());
    }

    private String classifyFilterShape(OptionTradeAggregateFilter filter) {
        boolean hasTicker = filter.tickers() != null && !filter.tickers().isEmpty();
        boolean hasRange = filter.minPremium() != null
            || filter.maxPremium() != null
            || filter.minVolume() != null
            || filter.maxVolume() != null
            || filter.minSize() != null
            || filter.maxSize() != null;
        boolean hasExpensive = Boolean.TRUE.equals(filter.volumeGtOi())
            || Boolean.TRUE.equals(filter.sizeGtOi())
            || Boolean.TRUE.equals(filter.otmOnly())
            || Boolean.TRUE.equals(filter.itmOnly())
            || Boolean.TRUE.equals(filter.excludeDeepItm())
            || isExcludedSideBucket(filter.bid())
            || isExcludedSideBucket(filter.ask())
            || isExcludedSideBucket(filter.noSide())
            || isExcludedSideBucket(filter.aboveAsk())
            || isExcludedSideBucket(filter.belowBid());

        return FlowFilterControllerSupport.classifyFilterShape(hasTicker, hasRange, hasExpensive);
    }

    private HotOptionTradeAggregateCacheService.HotAggregatePageKey buildHotAggregatePageKey(
        int page,
        int pageSize,
        BigDecimal minPremium,
        String tickers,
        String ignoreTickers,
        Boolean sweepOnly,
        Boolean floorOnly,
        String side,
        Instant since,
        Instant until,
        BigDecimal maxPremium,
        Long minSize,
        Long maxSize,
        Long minVolume,
        Long maxVolume,
        String expiration,
        Integer minDte,
        Integer maxDte,
        Boolean calls,
        Boolean puts,
        Boolean sweeps,
        Boolean floor,
        Boolean hasSingleleg,
        Boolean hasMultileg,
        Boolean cross,
        Boolean regular,
        Boolean bid,
        Boolean ask,
        Boolean noSide,
        Boolean aboveAsk,
        Boolean belowBid,
        Boolean volumeGtOi,
        Boolean sizeGtOi,
        Boolean excludeDeepItm,
        Boolean otmOnly,
        Boolean itmOnly,
        Boolean hideExpired,
        String sortBy,
        OptionTradeMarketDayResolver.ResolvedMarketDay resolvedDefaultDay,
        OptionTradeCountMode effectiveCountMode
    ) {
        boolean matchesDefaultMinPremium = minPremium != null
            && minPremium.compareTo(HotOptionTradeAggregateCacheService.defaultFlowPremium()) == 0;
        boolean matchesDefaultMinVolume = minVolume != null
            && minVolume == HotOptionTradeAggregateCacheService.defaultFlowVolume();

        boolean isDefaultPreset = !hasText(tickers)
            && !hasText(ignoreTickers)
            && sweepOnly == null
            && floorOnly == null
            && !hasText(side)
            && since == null
            && until == null
            && matchesDefaultMinPremium
            && maxPremium == null
            && minSize == null
            && maxSize == null
            && matchesDefaultMinVolume
            && maxVolume == null
            && !hasText(expiration)
            && minDte == null
            && maxDte == null
            && calls == null
            && puts == null
            && sweeps == null
            && floor == null
            && Boolean.TRUE.equals(hasSingleleg)
            && hasMultileg == null
            && cross == null
            && regular == null
            && bid == null
            && ask == null
            && noSide == null
            && aboveAsk == null
            && belowBid == null
            && volumeGtOi == null
            && sizeGtOi == null
            && excludeDeepItm == null
            && otmOnly == null
            && itmOnly == null
            && hideExpired == null
            && isDefaultSortBy(sortBy)
            && pageSize <= 200;

        if (!isDefaultPreset) {
            return null;
        }

        return new HotOptionTradeAggregateCacheService.HotAggregatePageKey(
            Math.max(1, page),
            pageSize,
            minPremium,
            resolvedDefaultDay != null ? resolvedDefaultDay.day() : DateUtils.getCurrentDateInEasternTime(),
            HotOptionTradeAggregateCacheService.defaultFlowFilterIdentity(),
            effectiveCountMode,
            isDefaultSortBy(sortBy) ? "sip_timestamp" : sortBy
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Sort buildSort(String sortBy) {
        String normalizedSortBy = sortBy == null ? "" : sortBy.trim().toLowerCase();

        return switch (normalizedSortBy) {
            case SORT_NET_PREMIUM, SORT_PREMIUM -> Sort.by(
                Sort.Order.desc(SORT_TOTAL_PREMIUM),
                Sort.Order.desc(SORT_LAST_EXECUTED_AT),
                Sort.Order.desc(SORT_AGGREGATE_ID)
            );
            case SORT_SIZE -> Sort.by(
                Sort.Order.desc(SORT_TOTAL_SIZE),
                Sort.Order.desc(SORT_TOTAL_PREMIUM),
                Sort.Order.desc(SORT_LAST_EXECUTED_AT),
                Sort.Order.desc(SORT_AGGREGATE_ID)
            );
            default -> Sort.by(
                Sort.Order.desc(SORT_LAST_EXECUTED_AT),
                Sort.Order.desc(SORT_AGGREGATE_ID)
            );
        };
    }

    private boolean isDefaultSortBy(String sortBy) {
        return sortBy == null || sortBy.isBlank() || "sip_timestamp".equalsIgnoreCase(sortBy.trim());
    }

}
