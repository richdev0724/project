package com.marketmono.controller;

import com.marketmono.dto.OptionTradeAggregateDto;
import com.marketmono.dto.OptionTradeAggregatePageResponse;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeDto;
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OptionTradeAggregatesControllerTest {

    @Mock
    private OptionTradeAggregateService aggregateService;

    @Mock
    private OptionTradeAggregateBroadcaster aggregateBroadcaster;

    @Spy
    private HotOptionTradeAggregateCacheService hotAggregateCacheService = new HotOptionTradeAggregateCacheService();

    @Spy
    private FlowFilterQueryCacheService flowFilterQueryCacheService = new FlowFilterQueryCacheService();

    @Mock
    private NodeIdentityService nodeIdentityService;

    @Mock
    private FlowFilterLoadGuard flowFilterLoadGuard;

    @Mock
    private OptionTradeMarketDayResolver marketDayResolver;

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    @InjectMocks
    private OptionTradeAggregatesController controller;

    @BeforeEach
    void allowFlowFilterQueriesByDefault() {
        lenient().when(flowFilterLoadGuard.tryAcquire(any(), any()))
            .thenReturn(FlowFilterLoadGuard.GuardPermit.noop());
        lenient().when(marketDayResolver.resolveAggregateDefaultDay()).thenReturn(Optional.empty());
    }

    @Test
    void shouldDelegateAggregateQueryWithNormalizedParams() {
        OptionTradeAggregateDto dto = aggregateDto(UUID.randomUUID(), "SPY", 2400L);

        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(sliceResult(List.of(dto), true));

        Instant since = Instant.parse("2026-03-18T13:00:00Z");
        Instant until = Instant.parse("2026-03-18T14:00:00Z");
        LocalDate todayEastern = DateUtils.getCurrentDateInEasternTime();

        ResponseEntity<OptionTradeAggregatePageResponse> response = invokeAggregateQueryWithExpandedFilters(since, until);

        ArgumentCaptor<OptionTradeAggregateFilter> filterCaptor = ArgumentCaptor.forClass(OptionTradeAggregateFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<OptionTradeCountMode> countModeCaptor = ArgumentCaptor.forClass(OptionTradeCountMode.class);
        verify(aggregateService).getAggregates(
            filterCaptor.capture(),
            pageableCaptor.capture(),
            countModeCaptor.capture()
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).containsExactly(dto);
        assertThat(response.getBody().totalCount()).isNull();
        assertThat(response.getBody().hasNext()).isTrue();
        assertThat(response.getBody().totalCountExact()).isFalse();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.SLICE);
        assertThat(countModeCaptor.getValue()).isEqualTo(OptionTradeCountMode.SLICE);

        OptionTradeAggregateFilter filter = filterCaptor.getValue();
        assertThat(filter.tickers()).containsExactly("SPY", "QQQ");
        assertThat(filter.ignoreTickers()).containsExactly("IWM", "DIA");
        assertThat(filter.since()).isEqualTo(since);
        assertThat(filter.until()).isEqualTo(until);
        assertThat(filter.expirations()).containsExactly(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 21));
        assertThat(filter.minExpiryDate()).isEqualTo(todayEastern.plusDays(1));
        assertThat(filter.maxExpiryDate()).isEqualTo(todayEastern.plusDays(30));
        assertThat(filter.minPremium()).isEqualByComparingTo("100000.00");
        assertThat(filter.maxPremium()).isEqualByComparingTo("250000.00");
        assertThat(filter.minSize()).isEqualTo(25L);
        assertThat(filter.maxSize()).isEqualTo(250L);
        assertThat(filter.minVolume()).isEqualTo(100L);
        assertThat(filter.maxVolume()).isEqualTo(5000L);
        assertThat(filter.calls()).isFalse();
        assertThat(filter.puts()).isTrue();
        assertThat(filter.sweepOnly()).isTrue();
        assertThat(filter.floorOnly()).isNull();
        assertThat(filter.hasSweep()).isFalse();
        assertThat(filter.hasFloor()).isNull();
        assertThat(filter.hasSingleleg()).isFalse();
        assertThat(filter.hasMultileg()).isNull();
        assertThat(filter.side()).isEqualTo("ASK_SIDE");
        assertThat(filter.cross()).isFalse();
        assertThat(filter.bid()).isFalse();
        assertThat(filter.noSide()).isFalse();
        assertThat(filter.belowBid()).isFalse();
        assertThat(filter.volumeGtOi()).isTrue();
        assertThat(filter.excludeDeepItm()).isTrue();
        assertThat(filter.itmOnly()).isTrue();
        assertThat(filter.hideExpired()).isTrue();

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(500);
        assertThat(pageable.getSort().getOrderFor("lastExecutedAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("lastExecutedAt").getDirection().isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("aggregateId")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("aggregateId").getDirection().isDescending()).isTrue();
    }

    @Test
    void shouldDefaultSinceToResolvedAggregateMarketDayWhenMissing() {
        LocalDate latestRawDay = LocalDate.of(2026, 5, 15);
        when(marketDayResolver.resolveAggregateDefaultDay()).thenReturn(Optional.of(resolvedMarketDay(
            latestRawDay,
            OptionTradeMarketDayResolver.SOURCE_RAW,
            latestRawDay,
            latestRawDay
        )));
        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(sliceResult(List.of(), false));

        invokeUnfilteredAggregateQuery(1, 100);

        ArgumentCaptor<OptionTradeAggregateFilter> filterCaptor = ArgumentCaptor.forClass(OptionTradeAggregateFilter.class);
        verify(aggregateService).getAggregates(filterCaptor.capture(), any(Pageable.class), any());

        Instant effectiveSince = filterCaptor.getValue().since();
        assertThat(effectiveSince).isEqualTo(DateUtils.startOfEasternDay(latestRawDay));
        assertThat(filterCaptor.getValue().until()).isEqualTo(DateUtils.endOfEasternDay(latestRawDay));
        assertThat(filterCaptor.getValue().tickers()).isNull();
    }

    @Test
    void shouldUseHotAggregateCacheForResolvedLatestDayDefault() {
        LocalDate latestRawDay = LocalDate.of(2026, 5, 15);
        when(marketDayResolver.resolveAggregateDefaultDay()).thenReturn(Optional.of(resolvedMarketDay(
            latestRawDay,
            OptionTradeMarketDayResolver.SOURCE_RAW,
            latestRawDay,
            latestRawDay
        )));
        OptionTradeAggregatePageResponse cached = new OptionTradeAggregatePageResponse(
            List.of(aggregateDto(UUID.randomUUID(), "SPY", 2400L)),
            null,
            false,
            false,
            OptionTradeCountMode.SLICE
        );
        doReturn(cached).when(hotAggregateCacheService).getIfPresent(argThat(key ->
            key != null && latestRawDay.equals(key.resolvedDay())
        ));

        ResponseEntity<OptionTradeAggregatePageResponse> response = invokeDefaultHotAggregateQuery(null);

        assertThat(response.getBody()).isEqualTo(cached);
        verifyNoInteractions(aggregateService);
        verify(hotAggregateCacheService).getIfPresent(argThat(key ->
            key != null && latestRawDay.equals(key.resolvedDay())
        ));
    }

    @Test
    void shouldShortCircuitImpossibleAggregateSideBucketExclusion() {
        ResponseEntity<OptionTradeAggregatePageResponse> response = controller.getAggregates(
            1,
            100,
            null,
            new BigDecimal("50000"),
            null,
            null,
            null,
            Instant.parse("2026-05-15T04:00:00Z"),
            Instant.parse("2026-05-16T03:59:59Z"),
            null,
            null,
            null,
            null,
            100L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            "sip_timestamp",
            "slice"
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).isEmpty();
        assertThat(response.getBody().totalCount()).isNull();
        assertThat(response.getBody().hasNext()).isFalse();
        assertThat(response.getBody().totalCountExact()).isFalse();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.SLICE);
        verify(flowFilterQueryCacheService, never()).getOrLoad(any(), anyBoolean(), any(), any());
        verify(hotAggregateCacheService, never()).getIfPresent(any());
        verifyNoInteractions(aggregateService, flowFilterLoadGuard);
    }

    @Test
    void shouldShortCircuitImpossibleAggregateSideBucketExclusionForExactMode() {
        ResponseEntity<OptionTradeAggregatePageResponse> response = controller.getAggregates(
            1,
            100,
            null,
            new BigDecimal("50000"),
            null,
            null,
            null,
            Instant.parse("2026-05-15T04:00:00Z"),
            Instant.parse("2026-05-16T03:59:59Z"),
            null,
            null,
            null,
            null,
            100L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            "sip_timestamp",
            "exact"
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).isEmpty();
        assertThat(response.getBody().totalCount()).isZero();
        assertThat(response.getBody().hasNext()).isFalse();
        assertThat(response.getBody().totalCountExact()).isTrue();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.EXACT);
        verify(flowFilterQueryCacheService, never()).getOrLoad(any(), anyBoolean(), any(), any());
        verify(hotAggregateCacheService, never()).getIfPresent(any());
        verifyNoInteractions(aggregateService, flowFilterLoadGuard);
    }

    @Test
    void shouldAllowExplicitAggregateExactCountModeForCheapRequest() {
        OptionTradeAggregateDto dto = aggregateDto(UUID.randomUUID(), "SPY", 2400L);
        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(exactResult(List.of(dto), 42L, true));

        ResponseEntity<OptionTradeAggregatePageResponse> response =
            invokeUnfilteredAggregateQuery(1, 25, "exact");

        ArgumentCaptor<OptionTradeCountMode> countModeCaptor = ArgumentCaptor.forClass(OptionTradeCountMode.class);
        verify(aggregateService).getAggregates(any(), any(Pageable.class), countModeCaptor.capture());

        assertThat(countModeCaptor.getValue()).isEqualTo(OptionTradeCountMode.EXACT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).containsExactly(dto);
        assertThat(response.getBody().totalCount()).isEqualTo(42L);
        assertThat(response.getBody().hasNext()).isTrue();
        assertThat(response.getBody().totalCountExact()).isTrue();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.EXACT);
    }

    @Test
    void shouldRejectUnknownAggregateCountMode() {
        ResponseEntity<OptionTradeAggregatePageResponse> response =
            invokeUnfilteredAggregateQuery(1, 25, "precise");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(aggregateService);
    }

    @Test
    void shouldReturn503ForAggregateQueryTimeout() {
        ResponseEntity<Map<String, Object>> response = controller.handleAggregateQueryTimeout(
            new QueryTimeoutException("slow aggregate")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("code", "QUERY_TIMEOUT");
        assertThat(response.getBody()).containsEntry("message", "Option trade aggregate query timed out");
    }

    @Test
    void shouldReturnSanitizedErrorBodyWhenAggregateQueryTimesOut() throws Exception {
        when(nodeIdentityService.getNodeId()).thenReturn("backend-a");
        when(aggregateService.getAggregates(any(), any(Pageable.class), any()))
            .thenThrow(new QueryTimeoutException("slow aggregate"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/option-trades/aggregates")
                .param("tickers", "SPY, QQQ")
                .param("minPremium", "50000")
                .param("minVolume", "100")
                .param("volumeGtOi", "true")
                .param("sizeGtOi", "true")
                .param("otmOnly", "true")
                .param("hasMultileg", "true")
                .param("countMode", "slice"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("QUERY_TIMEOUT"))
            .andExpect(jsonPath("$.message").value("Option trade aggregate query timed out"))
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(jsonPath("$.endpoint").value("/api/v1/option-trades/aggregates"))
            .andExpect(jsonPath("$.filterSummary").doesNotExist())
            .andExpect(jsonPath("$.filterHash").doesNotExist())
            .andExpect(jsonPath("$.filterShape").doesNotExist())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("tickers"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("minPremium"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("minVolume"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("volumeGtOi"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("sizeGtOi"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("otmOnly"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("hasMultileg"))));
    }

    @Test
    void shouldReturn429WhenExpensiveAggregateQueryIsBusy() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);
        when(nodeIdentityService.getNodeId()).thenReturn("backend-a");
        when(flowFilterLoadGuard.tryAcquire(any(), any()))
            .thenReturn(FlowFilterLoadGuard.GuardPermit.busy(5));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/option-trades/aggregates")
                .param("tickers", "SPY, QQQ")
                .param("minPremium", "50000")
                .param("minVolume", "100")
                .param("volumeGtOi", "true")
                .param("sizeGtOi", "true")
                .param("otmOnly", "true")
                .param("hasMultileg", "true")
                .param("countMode", "slice"))
            .andExpect(status().isTooManyRequests())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "5"))
            .andExpect(jsonPath("$.code").value("EXPENSIVE_FILTER_BUSY"))
            .andExpect(jsonPath("$.message").value("Expensive Flow filter is busy. Narrow filters or retry shortly."))
            .andExpect(jsonPath("$.endpoint").value("/api/v1/option-trades/aggregates"))
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(jsonPath("$.retryAfterSeconds").value(5))
            .andExpect(jsonPath("$.filterSummary").doesNotExist())
            .andExpect(jsonPath("$.filterHash").doesNotExist())
            .andExpect(jsonPath("$.filterShape").doesNotExist())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("tickers"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("volumeGtOi"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("sizeGtOi"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("otmOnly"))));

        verifyNoInteractions(aggregateService);
        assertThat(meterRegistry.find("flow.filters.query.busy").counter()).isNotNull();
        assertThat(meterRegistry.getMeters())
            .noneMatch(meter -> meter.getId().getTag("filter_hash") != null);
    }

    @Test
    void shouldRecordBoundedAggregateFilterRequestAndResponseMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);
        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(sliceResult(
            List.of(
                aggregateDto(UUID.randomUUID(), "SPY", 2400L),
                aggregateDto(UUID.randomUUID(), "QQQ", 1800L)
            ),
            false
        ));

        ResponseEntity<OptionTradeAggregatePageResponse> response = invokeUnfilteredAggregateQuery(1, 75, "slice");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).hasSize(2);
        assertThat(meterRegistry.get("flow.filters.query.requests")
            .tag("endpoint", "aggregate")
            .tag("count_mode", "slice")
            .tag("outcome", "success")
            .tag("filter_shape", "default")
            .tag("page_size", "le_100")
            .counter()
            .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("flow.filters.query.response.rows")
            .tag("endpoint", "aggregate")
            .tag("count_mode", "slice")
            .tag("filter_shape", "default")
            .tag("page_size", "le_100")
            .summary()
            .totalAmount()).isEqualTo(2.0);
        assertThat(meterRegistry.getMeters())
            .noneMatch(meter -> meter.getId().getTag("filter_hash") != null);
    }

    @Test
    void shouldSortAggregatePremiumBeforePaginationWhenRequested() {
        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(sliceResult(List.of(), false));

        invokeSortedAggregateQuery("net_premium");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(aggregateService).getAggregates(any(), pageableCaptor.capture(), any());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("totalPremium")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("totalPremium").getDirection().isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("lastExecutedAt")).isNotNull();
    }

    @Test
    void shouldSortAggregateSizeBeforePaginationWhenRequested() {
        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(sliceResult(List.of(), false));

        invokeSortedAggregateQuery("size");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(aggregateService).getAggregates(any(), pageableCaptor.capture(), any());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("totalSize")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("totalSize").getDirection().isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("totalPremium")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("lastExecutedAt")).isNotNull();
    }

    @Test
    void shouldReturnBufferedRecentAggregatesWhenAvailable() {
        OptionTradeAggregateDto dto = aggregateDto(UUID.randomUUID(), "QQQ", null);
        OptionTradeAggregateDto hydrated = aggregateDto(dto.aggregateId(), "QQQ", 2400L);
        when(aggregateBroadcaster.getRecentAggregates(25)).thenReturn(List.of(dto));
        when(aggregateService.hydrateVolumes(List.of(dto))).thenReturn(List.of(hydrated));

        ResponseEntity<List<OptionTradeAggregateDto>> response = controller.getRecentAggregates(25);

        assertThat(response.getBody()).containsExactly(hydrated);
        verify(aggregateBroadcaster).getRecentAggregates(25);
        verify(aggregateService).hydrateVolumes(List.of(dto));
    }

    @Test
    void shouldFallbackToServiceRecentAggregatesWhenBufferIsEmpty() {
        OptionTradeAggregateDto dto = aggregateDto(UUID.randomUUID(), "IWM", 1800L);
        LocalDate latestRawDay = LocalDate.of(2026, 5, 15);
        when(marketDayResolver.resolveAggregateDefaultDay()).thenReturn(Optional.of(resolvedMarketDay(
            latestRawDay,
            OptionTradeMarketDayResolver.SOURCE_RAW,
            latestRawDay,
            latestRawDay
        )));
        when(aggregateBroadcaster.getRecentAggregates(40)).thenReturn(List.of());
        when(aggregateService.getRecentAggregates(
            40,
            DateUtils.startOfEasternDay(latestRawDay),
            DateUtils.endOfEasternDay(latestRawDay)
        )).thenReturn(List.of(dto));

        ResponseEntity<List<OptionTradeAggregateDto>> response = controller.getRecentAggregates(40);

        assertThat(response.getBody()).containsExactly(dto);
        verify(aggregateBroadcaster).getRecentAggregates(40);
        verify(aggregateService).getRecentAggregates(
            40,
            DateUtils.startOfEasternDay(latestRawDay),
            DateUtils.endOfEasternDay(latestRawDay)
        );
    }

    @Test
    void shouldReturnAggregateConstituents() {
        UUID aggregateId = UUID.randomUUID();
        OptionTradeDto dto = tradeDto(UUID.randomUUID(), Instant.parse("2026-03-18T14:02:00Z"));
        when(aggregateService.getConstituents(aggregateId)).thenReturn(List.of(dto));

        ResponseEntity<List<OptionTradeDto>> response = controller.getConstituents(aggregateId);

        assertThat(response.getBody()).containsExactly(dto);
        verify(aggregateService).getConstituents(aggregateId);
    }

    @Test
    void shouldCacheDefaultHotAggregatePresetForShortWindow() {
        OptionTradeAggregateDto dto = aggregateDto(UUID.randomUUID(), "SPY", 2400L);
        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(sliceResult(List.of(dto), false));

        ResponseEntity<OptionTradeAggregatePageResponse> first = invokeDefaultHotAggregateQuery(null);
        ResponseEntity<OptionTradeAggregatePageResponse> second = invokeDefaultHotAggregateQuery(null);

        assertThat(first.getBody()).isEqualTo(second.getBody());
        verify(aggregateService).getAggregates(any(), any(), any());
    }

    @Test
    void shouldBypassHotAggregateCacheWhenCustomFiltersArePresent() {
        OptionTradeAggregateDto dto = aggregateDto(UUID.randomUUID(), "SPY", 2400L);
        when(aggregateService.getAggregates(any(), any(), any())).thenReturn(sliceResult(List.of(dto), false));

        invokeDefaultHotAggregateQuery("SPY");
        invokeDefaultHotAggregateQuery("SPY");

        verify(hotAggregateCacheService, never()).getIfPresent(any());
        verify(aggregateService).getAggregates(any(), any(), any());
    }

    private static OptionTradeAggregateQueryResult sliceResult(
        List<OptionTradeAggregateDto> results,
        boolean hasNext
    ) {
        return OptionTradeAggregateQueryResult.slice(results, hasNext);
    }

    private static OptionTradeAggregateQueryResult exactResult(
        List<OptionTradeAggregateDto> results,
        long totalCount,
        boolean hasNext
    ) {
        return OptionTradeAggregateQueryResult.exact(results, totalCount, hasNext);
    }

    private static OptionTradeMarketDayResolver.ResolvedMarketDay resolvedMarketDay(
        LocalDate day,
        String source,
        LocalDate rawMaxDay,
        LocalDate aggregateMaxDay
    ) {
        return new OptionTradeMarketDayResolver.ResolvedMarketDay(
            day,
            DateUtils.startOfEasternDay(day),
            DateUtils.endOfEasternDay(day),
            source,
            rawMaxDay,
            aggregateMaxDay
        );
    }

    private static OptionTradeAggregateDto aggregateDto(UUID aggregateId, String ticker, Long volume) {
        return new OptionTradeAggregateDto(
            aggregateId,
            1,
            ticker,
            ticker + "_260320_C_510",
            "ASK_SIDE",
            "ASK_SIDE",
            "AUTO",
            true,
            false,
            false,
            Instant.parse("2026-03-18T14:00:00Z"),
            Instant.parse("2026-03-18T14:00:01Z"),
            new BigDecimal("1.0100"),
            new BigDecimal("1.0000"),
            new BigDecimal("1.0200"),
            30L,
            new BigDecimal("3000.00"),
            2,
            2,
            LocalDate.of(2026, 3, 20),
            "call",
            new BigDecimal("510.00"),
            new BigDecimal("500.00"),
            new BigDecimal("500.05"),
            1500,
            volume
        );
    }

    private static OptionTradeDto tradeDto(UUID tradeId, Instant executedAt) {
        return new OptionTradeDto(
            tradeId,
            "SPY",
            "SPY_260320_C_510",
            new BigDecimal("1.05"),
            10,
            new BigDecimal("1050.00"),
            new BigDecimal("500.00"),
            new BigDecimal("1.00"),
            new BigDecimal("1.10"),
            "CBOE",
            "BUY",
            "AUTO",
            "BUY",
            true,
            false,
            false,
            executedAt,
            LocalDate.of(2026, 3, 20),
            "call",
            1500,
            new BigDecimal("510.00"),
            1200L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private ResponseEntity<OptionTradeAggregatePageResponse> invokeAggregateQueryWithExpandedFilters(
        Instant since,
        Instant until
    ) {
        return controller.getAggregates(
            2,
            999,
            "spy, qqq ",                 // tickers
            new BigDecimal("100000.00"), // minPremium
            true,                         // sweepOnly
            null,                         // floorOnly
            "ask_side",                  // side
            since,
            until,
            "iwm, dia ",                 // ignoreTickers
            new BigDecimal("250000.00"), // maxPremium
            25L,                          // minSize
            250L,                         // maxSize
            100L,                         // minVolume
            5000L,                        // maxVolume
            "2026-03-20, 2026-03-21",    // expiration
            1,                            // minDte
            30,                           // maxDte
            false,                        // calls
            true,                         // puts
            false,                        // sweeps
            null,                         // floor
            false,                        // hasSingleleg
            null,                         // hasMultileg
            false,                        // cross
            null,                         // regular
            false,                        // bid
            null,                         // ask
            false,                        // noSide
            null,                         // aboveAsk
            false,                        // belowBid
            true,                         // volumeGtOi
            null,                         // sizeGtOi
            true,                         // excludeDeepItm
            null,                         // otmOnly
            true,                         // itmOnly
            true,                         // hideExpired
            "sip_timestamp",              // sortBy
            "slice"                       // countMode
        );
    }

    private ResponseEntity<OptionTradeAggregatePageResponse> invokeUnfilteredAggregateQuery(int page, int pageSize) {
        return invokeUnfilteredAggregateQuery(page, pageSize, "slice");
    }

    private ResponseEntity<OptionTradeAggregatePageResponse> invokeUnfilteredAggregateQuery(
        int page,
        int pageSize,
        String countMode
    ) {
        return controller.getAggregates(
            page,
            pageSize,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            countMode
        );
    }

    private ResponseEntity<OptionTradeAggregatePageResponse> invokeSortedAggregateQuery(String sortBy) {
        return controller.getAggregates(
            1,
            150,
            null,
            new BigDecimal("50000"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            100L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            sortBy,
            "slice"
        );
    }

    private ResponseEntity<OptionTradeAggregatePageResponse> invokeDefaultHotAggregateQuery(String tickers) {
        return controller.getAggregates(
            1,                          // page
            150,                        // pageSize
            tickers,                    // tickers
            new BigDecimal("50000"),   // minPremium
            null,                       // sweepOnly
            null,                       // floorOnly
            null,                       // side
            null,                       // since
            null,                       // until
            null,                       // ignoreTickers
            null,                       // maxPremium
            null,                       // minSize
            null,                       // maxSize
            100L,                       // minVolume
            null,                       // maxVolume
            null,                       // expiration
            null,                       // minDte
            null,                       // maxDte
            null,                       // calls
            null,                       // puts
            null,                       // sweeps
            null,                       // floor
            true,                       // hasSingleleg
            null,                       // hasMultileg
            null,                       // cross
            null,                       // regular
            null,                       // bid
            null,                       // ask
            null,                       // noSide
            null,                       // aboveAsk
            null,                       // belowBid
            null,                       // volumeGtOi
            null,                       // sizeGtOi
            null,                       // excludeDeepItm
            null,                       // otmOnly
            null,                       // itmOnly
            null,                       // hideExpired
            "sip_timestamp",            // sortBy
            "slice"                     // countMode
        );
    }

}
