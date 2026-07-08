package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.OptionTradesProperties;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.FlowAlertsQueryRequest;
import com.marketmono.dto.OptionContractsResponse;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeDto;
import com.marketmono.dto.OptionTradePageResponse;
import com.marketmono.dto.UnusualWhalesFlowResponse;
import com.marketmono.entity.BookmarkSourceType;
import com.marketmono.exception.FlowFilterBusyException;
import com.marketmono.service.FlowFilterLoadGuard;
import com.marketmono.service.FlowFilterQueryCacheService;
import com.marketmono.service.NodeIdentityService;
import com.marketmono.service.OptionTradeBookmarkService;
import com.marketmono.service.OptionTradeBroadcaster;
import com.marketmono.service.OptionTradeMarketDayResolver;
import com.marketmono.service.OptionTradeService;
import com.marketmono.service.OptionTradeService.OptionTradeFilter;
import com.marketmono.service.OptionTradeService.OptionTradeQueryResult;
import com.marketmono.service.UnusualWhalesQueryParamMapper;
import com.marketmono.util.DateUtils;
import com.marketmono.util.Pagination;
import com.marketmono.util.RequestParamUtils;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.marketmono.controller.FlowFilterControllerSupport.*;

/**
 * REST controller for option trades endpoints
 * Provides direct access to upstream market data provider option flow data
 */
@RestController
@RequestMapping("/api/v1/option-trades")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class OptionTradesController {
    private static final String ENDPOINT_TAG_RAW = "raw";
    private static final String QUERY_TIMEOUT_MESSAGE = "Option trades query timed out";
    private static final String SORT_EXECUTED_AT = "executedAt";
    private static final String SORT_TRADE_ID = "tradeId";
    
    private final UnusualWhalesClient unusualWhalesClient;
    private final UnusualWhalesQueryParamMapper unusualWhalesQueryParamMapper;
    private final OptionTradeService optionTradeService;
    private final OptionTradeBookmarkService bookmarkService;
    private final OptionTradeBroadcaster tradeBroadcaster;
    private final OptionTradesProperties optionTradesProperties;
    private final FlowFilterLoadGuard flowFilterLoadGuard;
    private final NodeIdentityService nodeIdentityService;
    private final OptionTradeMarketDayResolver marketDayResolver;
    private final FlowFilterQueryCacheService flowFilterQueryCacheService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    
    /**
     * Get flow alerts from the upstream market data provider.
     * Endpoint: GET /api/v1/option-trades/flow-alerts
     * 
     * Compatibility route for older frontend callers.
     * 
     * @param request bound flow alert query parameters
     * @return Upstream flow alerts in the legacy alert-service response envelope
     */
    @GetMapping("/flow-alerts")
    public ResponseEntity<UnusualWhalesFlowResponse> getFlowAlerts(
            @ParameterObject @ModelAttribute FlowAlertsQueryRequest request
    ) {
        log.info("GET /api/v1/option-trades/flow-alerts - ticker: {}, minDte: {}, maxDte: {}, limit: {}", 
            request.tickerSymbol(), request.minDte(), request.maxDte(), request.limit());
        
        try {
            Map<String, Object> params = unusualWhalesQueryParamMapper.toParams(request);
            UnusualWhalesFlowResponse response = unusualWhalesClient.getFlowAlertsUncached(params);
            if (response == null) {
                log.warn("No flow alerts response available from upstream market data API");
                return ResponseEntity.ok(new UnusualWhalesFlowResponse(List.of(), null, null));
            }

            int resultCount = response.data() != null ? response.data().size() : 0;
            log.info("Returning {} upstream flow alerts", resultCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching flow alerts from upstream market data API", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get option contracts from screener API
     * Endpoint: GET /api/v1/screener/option-contracts
     * 
     * Proxies the upstream market data endpoint for option contract screening.
     * 
        * @param request bound option contract screener query parameters
     * @return Option contracts response from the upstream market data API
     */
    @GetMapping("/screener/option-contracts")
    public ResponseEntity<OptionContractsResponse> getOptionContracts(
            @RequestParam MultiValueMap<String, String> requestParams
    ) {
        log.info("GET /api/v1/screener/option-contracts - ticker: {}, limit: {}",
            requestParams.getFirst("ticker_symbol"),
            requestParams.getFirst("limit"));
        
        try {
            Map<String, Object> params = unusualWhalesQueryParamMapper.toParams(requestParams);
            
            // Call the upstream market data API.
            OptionContractsResponse response = unusualWhalesClient.getOptionContracts(params);
            
            if (response != null && response.data() != null) {
                log.info("Returning {} option contracts from upstream market data API", response.data().size());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No option contracts data available from upstream market data API");
                return ResponseEntity.ok(new OptionContractsResponse(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching option contracts from upstream market data API", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get option trades from local DB (ingested via Kafka).
     * Paginated with filtering support.
     * GET /api/v1/option-trades?page=1&pageSize=100&tickers=AAPL,MSFT&minPremium=10000&sweepOnly=true
     */
    @GetMapping
    public ResponseEntity<OptionTradePageResponse> getOptionTrades(
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
            @RequestParam(required = false) Boolean mid,
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

        ResolvedFlowWindow flowWindow = resolveFlowWindow(since, until, marketDayResolver::resolveRawDefaultDay);
        LocalDate todayEastern = DateUtils.getCurrentDateInEasternTime();
        LocalDate minExpiryDate = minDte != null ? todayEastern.plusDays(minDte) : null;
        LocalDate maxExpiryDate = maxDte != null ? todayEastern.plusDays(maxDte) : null;
        String normalizedSide = RequestParamUtils.normalizeSide(side);

        OptionTradeFilter filter = OptionTradeFilter.builder()
            .tickers(tickerList)
            .ignoreTickers(ignoreTickerList)
            .since(flowWindow.since())
            .until(flowWindow.until())
            .expirations(expiryList)
            .minExpiryDate(minExpiryDate)
            .maxExpiryDate(maxExpiryDate)
            .minPremium(minPremium)
            .maxPremium(maxPremium)
            .minSize(minSize)
            .maxSize(maxSize)
            .minVolume(minVolume)
            .maxVolume(maxVolume)
            .calls(calls)
            .puts(puts)
            .sweepOnly(sweepOnly)
            .floorOnly(floorOnly)
            .hasSweep(sweeps != null && !sweeps ? Boolean.FALSE : null)
            .hasFloor(floor != null && !floor ? Boolean.FALSE : null)
            .hasSingleleg(hasSingleleg)
            .hasMultileg(hasMultileg)
            .side(normalizedSide)
            .cross(cross)
            .regular(regular)
            .bid(bid)
            .mid(mid)
            .ask(ask)
            .noSide(noSide)
            .aboveAsk(aboveAsk)
            .belowBid(belowBid)
            .volumeGtOi(volumeGtOi)
            .sizeGtOi(sizeGtOi)
            .excludeDeepItm(excludeDeepItm)
            .otmOnly(otmOnly)
            .itmOnly(itmOnly)
            .hideExpired(hideExpired)
            .build();

        OptionTradeCountMode requestedCountMode;
        try {
            requestedCountMode = OptionTradeCountMode.fromQueryParam(countMode);
        } catch (IllegalArgumentException ex) {
            log.warn("Rejecting invalid countMode for /api/v1/option-trades: {}", countMode);
            return ResponseEntity.badRequest().build();
        }
        OptionTradeCountMode effectiveCountMode = excludesAllRawSideBuckets(filter)
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

        if (excludesAllRawSideBuckets(filter)) {
            long durationMs = elapsedMillis(startedAtNanos);
            attribution.put(ATTR_DURATION_MS, durationMs);
            attribution.put(ATTR_OUTCOME, OUTCOME_SHORT_CIRCUIT);
            attribution.put(ATTR_RESPONSE_ROWS, 0);
            recordFlowFilterQueryMetric(attribution, OUTCOME_SHORT_CIRCUIT, durationMs);
            log.debug("[PERF] Option-trades side filter short-circuited to empty result: {}", attribution);
            return ResponseEntity.ok(emptyTradeResponse(effectiveCountMode));
        }

        FlowFilterQueryCacheService.FlowFilterQueryCacheKey cacheKey =
            buildFlowFilterQueryCacheKey(attribution, effectiveCountMode);
        boolean cacheResult = effectiveCountMode == OptionTradeCountMode.SLICE;
        OptionTradePageResponse response = getOrLoadFlowFilterResult(
            flowFilterQueryCacheService,
            cacheKey,
            cacheResult,
            OptionTradePageResponse.class,
            () -> loadOptionTrades(startedAtNanos, attribution, filter, pageable, effectiveCountMode),
            new FlowFilterOperation(
                startedAtNanos,
                attribution,
                ENDPOINT_TAG_RAW,
                meterRegistryProvider.getIfAvailable(),
                log,
                "Option-trades",
                QUERY_TIMEOUT_MESSAGE
            )
        );

        return ResponseEntity.ok(response);
    }

    private static boolean excludesAllRawSideBuckets(OptionTradeFilter filter) {
        return Boolean.FALSE.equals(filter.bid())
            && Boolean.FALSE.equals(filter.mid())
            && Boolean.FALSE.equals(filter.ask())
            && Boolean.FALSE.equals(filter.noSide())
            && Boolean.FALSE.equals(filter.aboveAsk())
            && Boolean.FALSE.equals(filter.belowBid());
    }

    private static OptionTradePageResponse emptyTradeResponse(OptionTradeCountMode countMode) {
        if (countMode == OptionTradeCountMode.EXACT) {
            return OptionTradePageResponse.exact(List.of(), 0L, false);
        }
        return OptionTradePageResponse.slice(List.of(), false);
    }

    private OptionTradePageResponse loadOptionTrades(
        long startedAtNanos,
        Map<String, Object> attribution,
        OptionTradeFilter filter,
        PageRequest pageable,
        OptionTradeCountMode effectiveCountMode
    ) {
        OptionTradeQueryResult result = runGuardedFlowFilterQuery(
            flowFilterLoadGuard,
            () -> optionTradeService.getTrades(filter, pageable, effectiveCountMode),
            queryResult -> queryResult.results().size(),
            new FlowFilterOperation(
                startedAtNanos,
                attribution,
                ENDPOINT_TAG_RAW,
                meterRegistryProvider.getIfAvailable(),
                log,
                "option-trades",
                QUERY_TIMEOUT_MESSAGE
            )
        );

        return toPageResponse(result);
    }
    
    /**
     * Get option trades for a specific ticker from local DB.
     * GET /api/v1/option-trades/ticker/{ticker}?page=1&pageSize=100
     */
    @GetMapping("/ticker/{ticker}")
    public ResponseEntity<OptionTradePageResponse> getOptionTradesByTicker(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize
    ) {
        PageRequest pageable = Pagination.cappedPageRequest(
            page,
            pageSize,
            500,
            Sort.by(Sort.Direction.DESC, SORT_EXECUTED_AT)
        );
        Page<OptionTradeDto> result = optionTradeService.getTradesByTicker(ticker.toUpperCase(), pageable);
        
        return ResponseEntity.ok(OptionTradePageResponse.exact(result.getContent(), result.getTotalElements(), result.hasNext()));
    }
    
    /**
     * Get aggregate stats for option trades (summary for dashboard).
     * GET /api/v1/option-trades/stats?hours=1
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTradeStats(
            @RequestParam(defaultValue = "1") int hours
    ) {
        int clampedHours = Math.min(Math.max(1, hours), 24);
        Instant since = Instant.now().minus(clampedHours, ChronoUnit.HOURS);
        
        Map<String, Object> stats = optionTradeService.getAggregateStats(since);
        if (stats == null || stats.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("tradeCount", stats.get("trade_count"));
        result.put("totalVolume", stats.get("total_volume"));
        result.put("totalPremium", stats.get("total_premium"));
        result.put("sweepCount", stats.get("sweep_count"));
        result.put("floorCount", stats.get("floor_count"));
        result.put("multilegCount", stats.get("multileg_count"));
        result.put("since", since.toString());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Get recent trades from the in-memory circular buffer.
         * Instant page-load data (no DB hit). Falls back to a bounded latest-qualifying DB snapshot if the buffer is empty.
     * GET /api/v1/option-trades/recent?limit=500
     */
    @GetMapping("/recent")
    public ResponseEntity<List<OptionTradeDto>> getRecentTrades(
            @RequestParam(defaultValue = "500") int limit
    ) {
        List<OptionTradeDto> recent = tradeBroadcaster.getRecentTrades(limit);
        if (!recent.isEmpty()) {
            return ResponseEntity.ok(recent);
        }

        int cappedLimit = Math.min(Math.max(1, limit), 2000);
        OptionTradesProperties.Recent recentProperties = optionTradesProperties.getRecent();
        OptionTradeMarketDayResolver.ResolvedMarketDay resolvedDefaultDay =
            marketDayResolver.resolveRawDefaultDay().orElse(null);
        Instant fallbackSince = resolvedDefaultDay != null
            ? resolvedDefaultDay.since()
            : Instant.now().minus(recentProperties.getFallbackWindow());
        Instant fallbackUntil = resolvedDefaultDay != null ? resolvedDefaultDay.until() : null;

        // Cold start fallback: read the latest live-eligible trades from DB.
        PageRequest pageable = PageRequest.of(
            0,
            cappedLimit,
            Sort.by(Sort.Direction.DESC, SORT_EXECUTED_AT).and(Sort.by(Sort.Direction.DESC, SORT_TRADE_ID))
        );
        OptionTradeFilter filter = OptionTradeFilter.builder()
            .since(fallbackSince)
            .until(fallbackUntil)
            .minPremium(recentProperties.getFallbackMinPremium())
            .minVolume(recentProperties.getFallbackMinVolume())
            .hasSingleleg(true)
            .build();

        try {
            OptionTradeQueryResult result = optionTradeService.getTrades(filter, pageable, OptionTradeCountMode.SLICE);
            recordRecentFallbackMetrics(result.results().size());
            log.warn(
                "[RECENT-FALLBACK] DB fallback fired - buffer empty (local + Redis). limit={} window={} resolvedDaySource={} resolvedDay={} returned={}",
                cappedLimit,
                recentProperties.getFallbackWindow(),
                resolvedDefaultDay != null ? resolvedDefaultDay.source() : "window",
                resolvedDefaultDay != null ? resolvedDefaultDay.day() : null,
                result.results().size()
            );
            return ResponseEntity.ok(result.results());
        } catch (RuntimeException exception) {
            log.warn(
                "[RECENT-FALLBACK] DB fallback failed - buffer empty (local + Redis). limit={} window={} resolvedDaySource={} resolvedDay={} error={}",
                cappedLimit,
                recentProperties.getFallbackWindow(),
                resolvedDefaultDay != null ? resolvedDefaultDay.source() : "window",
                resolvedDefaultDay != null ? resolvedDefaultDay.day() : null,
                exception.getMessage(),
                exception
            );
            recordRecentFallbackFailureMetrics(exception);
            return ResponseEntity.ok(List.of());
        }
    }

    private void recordRecentFallbackMetrics(int rowsReturned) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter("option_trades.recent.fallback.count").increment();
        meterRegistry.summary("option_trades.recent.fallback.rows_returned").record(rowsReturned);
    }

    private void recordRecentFallbackFailureMetrics(RuntimeException exception) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
            "option_trades.recent.fallback.failures",
            "reason", classifyRecentFallbackFailure(exception)
        ).increment();
    }

    private void recordOptionTradeTimeoutMetric() {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter("option_trades.query.timeouts").increment();
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
            ENDPOINT_TAG_RAW
        );
    }

    private Map<String, Object> buildAttribution(
        UUID requestId,
        PageRequest pageable,
        OptionTradeFilter filter,
        String sortBy,
        OptionTradeCountMode requestedCountMode,
        OptionTradeCountMode effectiveCountMode
    ) {
        String filterSummary = summarizeFilter(filter, filter.mid());
        return buildFlowFilterAttribution(
            new FlowFilterAttributionContext(
                requestId,
                nodeIdentityService.getNodeId(),
                "/api/v1/option-trades",
                pageable,
                sortBy,
                requestedCountMode,
                effectiveCountMode
            ),
            filter,
            isRawSideFilterPresent(filter),
            filterSummary,
            classifyFilterShape(filter)
        );
    }

    private String classifyRecentFallbackFailure(Throwable throwable) {
        if (throwable instanceof QueryTimeoutException || throwable instanceof TransactionTimedOutException) {
            return OUTCOME_TIMEOUT;
        }

        return "other";
    }

    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class, FlowFilterQueryTimeoutException.class})
    public ResponseEntity<Map<String, Object>> handleOptionTradeQueryTimeout(RuntimeException exception) {
        if (exception instanceof FlowFilterQueryTimeoutException timeoutException) {
            recordOptionTradeTimeoutMetric();
            return ResponseEntity.status(503).body(timeoutBody(
                timeoutException.getMessage(),
                timeoutException.attribution()
            ));
        }

        log.warn("[PERF] Option-trades query timed out: {}", exception.getMessage(), exception);
        recordOptionTradeTimeoutMetric();
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

    private Sort buildSort(String sortBy) {
        String normalizedSortBy = sortBy == null ? "" : sortBy.trim().toLowerCase();

        return switch (normalizedSortBy) {
            case SORT_NET_PREMIUM, SORT_PREMIUM -> Sort.by(
                Sort.Order.desc(SORT_PREMIUM),
                Sort.Order.desc(SORT_EXECUTED_AT),
                Sort.Order.desc(SORT_TRADE_ID)
            );
            case SORT_SIZE -> Sort.by(
                Sort.Order.desc(SORT_SIZE),
                Sort.Order.desc(SORT_PREMIUM),
                Sort.Order.desc(SORT_EXECUTED_AT),
                Sort.Order.desc(SORT_TRADE_ID)
            );
            default -> Sort.by(
                Sort.Order.desc(SORT_EXECUTED_AT),
                Sort.Order.desc(SORT_TRADE_ID)
            );
        };
    }

    private OptionTradePageResponse toPageResponse(OptionTradeQueryResult result) {
        List<OptionTradeDto> results = List.copyOf(result.results());
        if (result.totalCountExact()) {
            return OptionTradePageResponse.exact(results, result.totalCount(), result.hasNext());
        }

        return OptionTradePageResponse.slice(results, result.hasNext());
    }

    private OptionTradeCountMode resolveEffectiveCountMode(
        OptionTradeCountMode requestedCountMode,
        OptionTradeFilter filter
    ) {
        if (requestedCountMode == OptionTradeCountMode.EXACT && hasExpensiveFlowPredicates(filter)) {
            log.info(
                "Using slice count mode for expensive option-trade filter set: sizeGtOi={}, volumeGtOi={}, otmOnly={}, itmOnly={}, excludeDeepItm={}",
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

    private boolean hasExpensiveFlowPredicates(OptionTradeFilter filter) {
        return Boolean.TRUE.equals(filter.sizeGtOi())
            || Boolean.TRUE.equals(filter.volumeGtOi())
            || Boolean.TRUE.equals(filter.otmOnly())
            || Boolean.TRUE.equals(filter.itmOnly())
            || Boolean.TRUE.equals(filter.excludeDeepItm())
            || isExcludedSideBucket(filter.bid())
            || isExcludedSideBucket(filter.mid())
            || isExcludedSideBucket(filter.ask())
            || isExcludedSideBucket(filter.noSide())
            || isExcludedSideBucket(filter.aboveAsk())
            || isExcludedSideBucket(filter.belowBid());
    }

    private boolean isRawSideFilterPresent(OptionTradeFilter filter) {
        return filter.side() != null && !filter.side().isBlank()
            || isExcludedSideBucket(filter.bid())
            || isExcludedSideBucket(filter.mid())
            || isExcludedSideBucket(filter.ask())
            || isExcludedSideBucket(filter.noSide())
            || isExcludedSideBucket(filter.aboveAsk())
            || isExcludedSideBucket(filter.belowBid());
    }

    private String classifyFilterShape(OptionTradeFilter filter) {
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
            || isExcludedSideBucket(filter.mid())
            || isExcludedSideBucket(filter.ask())
            || isExcludedSideBucket(filter.noSide())
            || isExcludedSideBucket(filter.aboveAsk())
            || isExcludedSideBucket(filter.belowBid());

        return FlowFilterControllerSupport.classifyFilterShape(hasTicker, hasRange, hasExpensive);
    }

    // ── Bookmark endpoints (authenticated) ───────────────────────────────

    /**
     * Get all bookmarked trade IDs for the authenticated user.
     * GET /api/v1/option-trades/bookmarks
     */
    @GetMapping("/bookmarks")
    public ResponseEntity<List<String>> getBookmarks(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(bookmarkService.getBookmarkedTradeIds(principal.getUser().getId()));
    }

    /**
     * Get full trade details for all bookmarked trades.
     * GET /api/v1/option-trades/bookmarks/trades
     */
    @GetMapping("/bookmarks/trades")
    public ResponseEntity<List<OptionTradeDto>> getBookmarkedTrades(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(bookmarkService.getBookmarkedTrades(principal.getUser().getId()));
    }

    /**
     * Add bookmarks for the authenticated user.
     * POST /api/v1/option-trades/bookmarks
     * Body: { "tradeIds": ["id1", "id2"], "sourceType": "FA" }
     * sourceType values: FA (Flow Alert, default), OT (Option Trade)
     */
    @PostMapping("/bookmarks")
    public ResponseEntity<Void> addBookmarks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        @SuppressWarnings("unchecked")
        List<String> tradeIds = (List<String>) body.getOrDefault("tradeIds", List.of());
        if (tradeIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String sourceStr = (String) body.getOrDefault("sourceType", "FA");
        BookmarkSourceType sourceType;
        try {
            sourceType = BookmarkSourceType.valueOf(sourceStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        bookmarkService.addBookmarks(principal.getUser().getId(), tradeIds, sourceType);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove bookmarks for the authenticated user.
     * POST /api/v1/option-trades/bookmarks/remove
     * Body: { "tradeIds": ["id1", "id2"] }
     */
    @PostMapping("/bookmarks/remove")
    public ResponseEntity<Void> removeBookmarks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, List<String>> body
    ) {
        List<String> tradeIds = body.getOrDefault("tradeIds", List.of());
        if (tradeIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        bookmarkService.removeBookmarks(principal.getUser().getId(), tradeIds);
        return ResponseEntity.ok().build();
    }
}
