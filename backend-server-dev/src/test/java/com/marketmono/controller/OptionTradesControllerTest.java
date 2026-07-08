package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.OptionTradesProperties;
import com.marketmono.dto.OptionContractsResponse;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeDto;
import com.marketmono.dto.OptionTradePageResponse;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.dto.UnusualWhalesFlowResponse;
import com.marketmono.service.NodeIdentityService;
import com.marketmono.service.FlowFilterLoadGuard;
import com.marketmono.service.FlowFilterQueryCacheService;
import com.marketmono.service.FlowAlertService;
import com.marketmono.service.OptionTradeBookmarkService;
import com.marketmono.service.OptionTradeBroadcaster;
import com.marketmono.service.OptionTradeMarketDayResolver;
import com.marketmono.service.OptionTradeService;
import com.marketmono.service.OptionTradeService.OptionTradeFilter;
import com.marketmono.service.OptionTradeService.OptionTradeQueryResult;
import com.marketmono.service.UnusualWhalesQueryParamMapper;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OptionTradesControllerTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private UnusualWhalesQueryParamMapper unusualWhalesQueryParamMapper;

    @Mock
    private OptionTradeService optionTradeService;

    @Mock
    private FlowAlertService flowAlertService;

    @Mock
    private OptionTradeBookmarkService bookmarkService;

    @Mock
    private OptionTradeBroadcaster tradeBroadcaster;

    @Mock
    private NodeIdentityService nodeIdentityService;

    @Mock
    private OptionTradeMarketDayResolver marketDayResolver;

    @Mock
    private FlowFilterLoadGuard flowFilterLoadGuard;

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Spy
    private FlowFilterQueryCacheService flowFilterQueryCacheService = new FlowFilterQueryCacheService();

    @Spy
    private OptionTradesProperties optionTradesProperties = new OptionTradesProperties();

    @InjectMocks
    private OptionTradesController controller;

    @BeforeEach
    void allowFlowFilterQueriesByDefault() {
        lenient().when(flowFilterLoadGuard.tryAcquire(any(), any()))
            .thenReturn(FlowFilterLoadGuard.GuardPermit.noop());
        lenient().when(marketDayResolver.resolveRawDefaultDay()).thenReturn(Optional.empty());
    }

    @Test
    void shouldDefaultToSliceCountMode() {
        OptionTradeDto dto = tradeDto();
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(OptionTradeQueryResult.slice(List.of(dto), true));

        ResponseEntity<OptionTradePageResponse> response = invokeBasicQuery(
            1,
            100,
            "spy, qqq ",
            new BigDecimal("50000"),
            100L,
            true,
            false,
            null
        );

        ArgumentCaptor<OptionTradeFilter> filterCaptor = ArgumentCaptor.forClass(OptionTradeFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<OptionTradeCountMode> countModeCaptor = ArgumentCaptor.forClass(OptionTradeCountMode.class);
        verify(optionTradeService).getTrades(filterCaptor.capture(), pageableCaptor.capture(), countModeCaptor.capture());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).containsExactly(dto);
        assertThat(response.getBody().totalCount()).isNull();
        assertThat(response.getBody().hasNext()).isTrue();
        assertThat(response.getBody().totalCountExact()).isFalse();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.SLICE);

        OptionTradeFilter filter = filterCaptor.getValue();
        assertThat(filter.tickers()).containsExactly("SPY", "QQQ");
        assertThat(filter.minPremium()).isEqualByComparingTo("50000");
        assertThat(filter.minVolume()).isEqualTo(100L);
        assertThat(filter.hasSingleleg()).isTrue();
        assertThat(filter.hasMultileg()).isFalse();
        assertThat(filter.until()).isNull();
        assertThat(filter.since()).isEqualTo(DateUtils.startOfCurrentEasternDay());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("executedAt")).isNotNull();
        assertThat(countModeCaptor.getValue()).isEqualTo(OptionTradeCountMode.SLICE);
    }

    @Test
    void shouldAllowExplicitExactCountModeForCheapRequest() {
        OptionTradeDto dto = tradeDto();
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(new OptionTradeQueryResult(List.of(dto), 250L, true, true, OptionTradeCountMode.EXACT));

        ResponseEntity<OptionTradePageResponse> response = invokeBasicQuery(
            1,
            100,
            "spy, qqq ",
            new BigDecimal("50000"),
            100L,
            true,
            false,
            "exact"
        );

        ArgumentCaptor<OptionTradeCountMode> countModeCaptor = ArgumentCaptor.forClass(OptionTradeCountMode.class);
        verify(optionTradeService).getTrades(any(), any(Pageable.class), countModeCaptor.capture());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalCount()).isEqualTo(250L);
        assertThat(response.getBody().totalCountExact()).isTrue();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.EXACT);
        assertThat(countModeCaptor.getValue()).isEqualTo(OptionTradeCountMode.EXACT);
    }

    @Test
    void shouldDefaultToLatestRawMarketDayWhenDateRangeMissing() {
        LocalDate latestRawDay = LocalDate.of(2026, 5, 15);
        when(marketDayResolver.resolveRawDefaultDay()).thenReturn(Optional.of(resolvedMarketDay(
            latestRawDay,
            OptionTradeMarketDayResolver.SOURCE_RAW,
            latestRawDay,
            latestRawDay
        )));
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(OptionTradeQueryResult.slice(List.of(), false));

        invokeUnfilteredQuery("slice");

        ArgumentCaptor<OptionTradeFilter> filterCaptor = ArgumentCaptor.forClass(OptionTradeFilter.class);
        verify(optionTradeService).getTrades(filterCaptor.capture(), any(Pageable.class), any());

        assertThat(filterCaptor.getValue().since()).isEqualTo(DateUtils.startOfEasternDay(latestRawDay));
        assertThat(filterCaptor.getValue().until()).isEqualTo(DateUtils.endOfEasternDay(latestRawDay));
    }

    @Test
    void shouldSortPremiumBeforePaginationWhenRequested() {
        OptionTradeDto dto = tradeDto();
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(OptionTradeQueryResult.slice(List.of(dto), false));

        ResponseEntity<OptionTradePageResponse> response = invokeSortedQuery("net_premium");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(optionTradeService).getTrades(any(), pageableCaptor.capture(), eq(OptionTradeCountMode.SLICE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("premium")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("premium").getDirection().isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("executedAt")).isNotNull();
    }

    @Test
    void shouldSortSizeBeforePaginationWhenRequested() {
        OptionTradeDto dto = tradeDto();
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(OptionTradeQueryResult.slice(List.of(dto), false));

        ResponseEntity<OptionTradePageResponse> response = invokeSortedQuery("size");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(optionTradeService).getTrades(any(), pageableCaptor.capture(), eq(OptionTradeCountMode.SLICE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("size")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("size").getDirection().isDescending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("premium")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("executedAt")).isNotNull();
    }

    @Test
    void shouldDelegateExpandedRawQueryWithNormalizedParams() {
        OptionTradeDto dto = tradeDto();
        PageResponseFixture fixture = new PageResponseFixture(dto);
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(fixture.sliceResult());

        Instant since = Instant.parse("2026-03-18T13:00:00Z");
        Instant until = Instant.parse("2026-03-18T14:00:00Z");
        LocalDate todayEastern = com.marketmono.util.DateUtils.getCurrentDateInEasternTime();

        ResponseEntity<OptionTradePageResponse> response = invokeExpandedQuery(since, until);

        ArgumentCaptor<OptionTradeFilter> filterCaptor = ArgumentCaptor.forClass(OptionTradeFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<OptionTradeCountMode> countModeCaptor = ArgumentCaptor.forClass(OptionTradeCountMode.class);
        verify(optionTradeService).getTrades(filterCaptor.capture(), pageableCaptor.capture(), countModeCaptor.capture());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).containsExactly(dto);
        assertThat(response.getBody().totalCount()).isNull();
        assertThat(response.getBody().hasNext()).isTrue();
        assertThat(response.getBody().totalCountExact()).isFalse();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.SLICE);

        OptionTradeFilter filter = filterCaptor.getValue();
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
        assertThat(filter.regular()).isFalse();
        assertThat(filter.bid()).isFalse();
        assertThat(filter.mid()).isFalse();
        assertThat(filter.ask()).isFalse();
        assertThat(filter.noSide()).isFalse();
        assertThat(filter.aboveAsk()).isNull();
        assertThat(filter.belowBid()).isFalse();
        assertThat(filter.volumeGtOi()).isTrue();
        assertThat(filter.sizeGtOi()).isTrue();
        assertThat(filter.excludeDeepItm()).isTrue();
        assertThat(filter.otmOnly()).isTrue();
        assertThat(filter.itmOnly()).isNull();
        assertThat(filter.hideExpired()).isTrue();

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(500);
        assertThat(pageable.getSort().getOrderFor("executedAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("tradeId")).isNotNull();
        assertThat(countModeCaptor.getValue()).isEqualTo(OptionTradeCountMode.SLICE);
    }

    @Test
    void shouldReturnSliceMetadataWhenRequested() {
        OptionTradeDto dto = tradeDto();
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(new OptionTradeQueryResult(List.of(dto), null, true, false, OptionTradeCountMode.SLICE));

        ResponseEntity<OptionTradePageResponse> response = invokeBasicQuery(
            2,
            75,
            "SPY",
            new BigDecimal("50000"),
            100L,
            true,
            false,
            "slice"
        );

        ArgumentCaptor<OptionTradeCountMode> countModeCaptor = ArgumentCaptor.forClass(OptionTradeCountMode.class);
        verify(optionTradeService).getTrades(any(), any(Pageable.class), countModeCaptor.capture());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).containsExactly(dto);
        assertThat(response.getBody().totalCount()).isNull();
        assertThat(response.getBody().hasNext()).isTrue();
        assertThat(response.getBody().totalCountExact()).isFalse();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.SLICE);
        assertThat(countModeCaptor.getValue()).isEqualTo(OptionTradeCountMode.SLICE);
    }

    @Test
    void shouldDefaultExpensiveFlowFiltersToSliceWhenCountModeIsMissing() {
        OptionTradeDto dto = tradeDto();
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(new OptionTradeQueryResult(List.of(dto), null, true, false, OptionTradeCountMode.SLICE));

        ResponseEntity<OptionTradePageResponse> response = controller.getOptionTrades(
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
            null,
            true,
            null,
            null,
            null,
            false,
            false,
            false,
            false,
            null,
            false,
            null,
            true,
            null,
            true,
            null,
            null,
            "sip_timestamp",
            null
        );

        ArgumentCaptor<OptionTradeFilter> filterCaptor = ArgumentCaptor.forClass(OptionTradeFilter.class);
        ArgumentCaptor<OptionTradeCountMode> countModeCaptor = ArgumentCaptor.forClass(OptionTradeCountMode.class);
        verify(optionTradeService).getTrades(filterCaptor.capture(), any(Pageable.class), countModeCaptor.capture());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalCountExact()).isFalse();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.SLICE);
        assertThat(countModeCaptor.getValue()).isEqualTo(OptionTradeCountMode.SLICE);

        OptionTradeFilter filter = filterCaptor.getValue();
        assertThat(filter.minPremium()).isEqualByComparingTo("50000");
        assertThat(filter.minVolume()).isEqualTo(100L);
        assertThat(filter.hasSingleleg()).isTrue();
        assertThat(filter.bid()).isFalse();
        assertThat(filter.mid()).isFalse();
        assertThat(filter.ask()).isFalse();
        assertThat(filter.noSide()).isFalse();
        assertThat(filter.belowBid()).isFalse();
        assertThat(filter.sizeGtOi()).isTrue();
        assertThat(filter.otmOnly()).isTrue();
    }

    @Test
    void shouldShortCircuitImpossibleRawSideBucketExclusion() {
        ResponseEntity<OptionTradePageResponse> response = controller.getOptionTrades(
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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).isEmpty();
        assertThat(response.getBody().totalCount()).isNull();
        assertThat(response.getBody().hasNext()).isFalse();
        assertThat(response.getBody().totalCountExact()).isFalse();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.SLICE);
        verify(flowFilterQueryCacheService, never()).getOrLoad(any(), anyBoolean(), any(), any());
        verifyNoInteractions(optionTradeService, flowFilterLoadGuard);
    }

    @Test
    void shouldShortCircuitImpossibleRawSideBucketExclusionForExactMode() {
        ResponseEntity<OptionTradePageResponse> response = controller.getOptionTrades(
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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).isEmpty();
        assertThat(response.getBody().totalCount()).isZero();
        assertThat(response.getBody().hasNext()).isFalse();
        assertThat(response.getBody().totalCountExact()).isTrue();
        assertThat(response.getBody().countMode()).isEqualTo(OptionTradeCountMode.EXACT);
        verify(flowFilterQueryCacheService, never()).getOrLoad(any(), anyBoolean(), any(), any());
        verifyNoInteractions(optionTradeService, flowFilterLoadGuard);
    }

    @Test
    void shouldNotShortCircuitRawSideBucketExclusionWhenMidIsAllowed() {
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(OptionTradeQueryResult.slice(List.of(), false));

        ResponseEntity<OptionTradePageResponse> response = controller.getOptionTrades(
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
            null,
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

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(optionTradeService).getTrades(any(), any(Pageable.class), eq(OptionTradeCountMode.SLICE));
    }

    @Test
    void shouldRejectUnknownCountMode() {
        ResponseEntity<OptionTradePageResponse> response = invokeUnfilteredQuery("banana");

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        verifyNoInteractions(optionTradeService);
    }

    @Test
    void shouldReturnRecentTradesFromBroadcasterWithoutDbFallback() {
        OptionTradeDto dto = tradeDto();
        when(tradeBroadcaster.getRecentTrades(25)).thenReturn(List.of(dto));

        ResponseEntity<List<OptionTradeDto>> response = controller.getRecentTrades(25);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(dto);
        verifyNoInteractions(optionTradeService);
    }

    @Test
    void shouldUseSliceFallbackWithBoundedSinceForRecentTrades() {
        optionTradesProperties.getRecent().setFallbackWindow(Duration.ofMinutes(45));
        optionTradesProperties.getRecent().setFallbackMinPremium(new BigDecimal("60000"));
        optionTradesProperties.getRecent().setFallbackMinVolume(150L);
        LocalDate latestRawDay = LocalDate.of(2026, 5, 15);
        when(marketDayResolver.resolveRawDefaultDay()).thenReturn(Optional.of(resolvedMarketDay(
            latestRawDay,
            OptionTradeMarketDayResolver.SOURCE_RAW,
            latestRawDay,
            latestRawDay
        )));

        OptionTradeDto dto = tradeDto();
        when(tradeBroadcaster.getRecentTrades(500)).thenReturn(List.of());
        when(optionTradeService.getTrades(any(), any(Pageable.class), eq(OptionTradeCountMode.SLICE)))
            .thenReturn(OptionTradeQueryResult.slice(List.of(dto), false));

        ResponseEntity<List<OptionTradeDto>> response = controller.getRecentTrades(500);

        ArgumentCaptor<OptionTradeFilter> filterCaptor = ArgumentCaptor.forClass(OptionTradeFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(optionTradeService).getTrades(filterCaptor.capture(), pageableCaptor.capture(), eq(OptionTradeCountMode.SLICE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(dto);

        OptionTradeFilter filter = filterCaptor.getValue();
        assertThat(filter.since()).isEqualTo(DateUtils.startOfEasternDay(latestRawDay));
        assertThat(filter.until()).isEqualTo(DateUtils.endOfEasternDay(latestRawDay));
        assertThat(filter.minPremium()).isEqualByComparingTo("60000");
        assertThat(filter.minVolume()).isEqualTo(150L);
        assertThat(filter.hasSingleleg()).isTrue();
        assertThat(filter.hasMultileg()).isNull();

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(500);
        assertThat(pageable.getSort().getOrderFor("executedAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("tradeId")).isNotNull();
    }

    @Test
    void shouldReturnEmptyListWhenRecentFallbackFails() {
        when(tradeBroadcaster.getRecentTrades(10)).thenReturn(List.of());
        when(optionTradeService.getTrades(any(), any(Pageable.class), eq(OptionTradeCountMode.SLICE)))
            .thenThrow(new RuntimeException("db is sad"));

        ResponseEntity<List<OptionTradeDto>> response = controller.getRecentTrades(10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void shouldReturn503ForOptionTradeQueryTimeout() {
        ResponseEntity<java.util.Map<String, Object>> response =
            controller.handleOptionTradeQueryTimeout(new QueryTimeoutException("too slow"));

        assertThat(response.getStatusCodeValue()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("code", "QUERY_TIMEOUT");
        assertThat(response.getBody()).containsEntry("message", "Option trades query timed out");
    }

    @Test
    void shouldReturnSanitizedErrorBodyWhenOptionTradeQueryTimesOut() throws Exception {
        when(nodeIdentityService.getNodeId()).thenReturn("backend-a");
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenThrow(new QueryTimeoutException("too slow"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/option-trades")
                .param("tickers", "SPY, QQQ")
                .param("minPremium", "50000")
                .param("minVolume", "100")
                .param("volumeGtOi", "true")
                .param("sizeGtOi", "true")
                .param("otmOnly", "true")
                .param("hasSingleleg", "true")
                .param("countMode", "slice"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("QUERY_TIMEOUT"))
            .andExpect(jsonPath("$.message").value("Option trades query timed out"))
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(jsonPath("$.endpoint").value("/api/v1/option-trades"))
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
                .string(org.hamcrest.Matchers.not(containsString("hasSingleleg"))));
    }

    @Test
    void shouldReturn429WhenExpensiveOptionTradeQueryIsBusy() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);
        when(nodeIdentityService.getNodeId()).thenReturn("backend-a");
        when(flowFilterLoadGuard.tryAcquire(any(), any()))
            .thenReturn(FlowFilterLoadGuard.GuardPermit.busy(5));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/option-trades")
                .param("tickers", "SPY, QQQ")
                .param("minPremium", "50000")
                .param("minVolume", "100")
                .param("volumeGtOi", "true")
                .param("sizeGtOi", "true")
                .param("otmOnly", "true")
                .param("hasSingleleg", "true")
                .param("countMode", "slice"))
            .andExpect(status().isTooManyRequests())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "5"))
            .andExpect(jsonPath("$.code").value("EXPENSIVE_FILTER_BUSY"))
            .andExpect(jsonPath("$.message").value("Expensive Flow filter is busy. Narrow filters or retry shortly."))
            .andExpect(jsonPath("$.endpoint").value("/api/v1/option-trades"))
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

        verifyNoInteractions(optionTradeService);
        assertThat(meterRegistry.find("flow.filters.query.busy").counter()).isNotNull();
        assertThat(meterRegistry.getMeters())
            .noneMatch(meter -> meter.getId().getTag("filter_hash") != null);
    }

    @Test
    void shouldRecordBoundedFlowFilterRequestAndResponseMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);
        when(optionTradeService.getTrades(any(), any(Pageable.class), any()))
            .thenReturn(new OptionTradeQueryResult(
                List.of(tradeDto(), tradeDto()),
                null,
                false,
                false,
                OptionTradeCountMode.SLICE
            ));

        ResponseEntity<OptionTradePageResponse> response = invokeBasicQuery(
            1,
            125,
            "SPY",
            new BigDecimal("50000"),
            100L,
            true,
            null,
            "slice"
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).hasSize(2);
        assertThat(meterRegistry.get("flow.filters.query.requests")
            .tag("endpoint", "raw")
            .tag("count_mode", "slice")
            .tag("outcome", "success")
            .tag("filter_shape", "ticker_range")
            .tag("page_size", "le_250")
            .counter()
            .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("flow.filters.query.response.rows")
            .tag("endpoint", "raw")
            .tag("count_mode", "slice")
            .tag("filter_shape", "ticker_range")
            .tag("page_size", "le_250")
            .summary()
            .totalAmount()).isEqualTo(2.0);
        assertThat(meterRegistry.getMeters())
            .noneMatch(meter -> meter.getId().getTag("filter_hash") != null);
    }

    @Test
    void shouldCacheRepeatedRawSliceRequestsByNormalizedKey() {
        OptionTradeDto dto = tradeDto();
        when(optionTradeService.getTrades(any(), any(Pageable.class), eq(OptionTradeCountMode.SLICE)))
            .thenReturn(OptionTradeQueryResult.slice(List.of(dto), false));

        ResponseEntity<OptionTradePageResponse> first = invokeBasicQuery(
            1,
            100,
            "spy, qqq",
            new BigDecimal("50000"),
            100L,
            true,
            null,
            "slice"
        );
        ResponseEntity<OptionTradePageResponse> second = invokeBasicQuery(
            1,
            100,
            "QQQ,SPY",
            new BigDecimal("50000.00"),
            100L,
            true,
            null,
            "slice"
        );

        assertThat(first.getBody()).isEqualTo(second.getBody());
        verify(optionTradeService).getTrades(any(), any(Pageable.class), eq(OptionTradeCountMode.SLICE));
    }

    @Test
    void shouldProxyLegacyFlowAlertsRouteToUpstreamMarketData() throws Exception {
        Instant createdAt = Instant.parse("2026-06-30T16:14:44Z");
        Instant olderThan = Instant.parse("2026-06-30T16:15:00Z");
        UnusualWhalesFlowAlertDto alert = upstreamFlowAlert(createdAt);
        UnusualWhalesFlowResponse upstreamResponse = new UnusualWhalesFlowResponse(
            List.of(alert),
            "2026-06-30T16:14:44.001Z",
            "2026-06-30T16:14:44.001Z"
        );
        Map<String, Object> upstreamParams = new LinkedHashMap<>();
        upstreamParams.put("ticker_symbol", "spy, qqq");
        upstreamParams.put("min_premium", 50_000L);
        upstreamParams.put("max_premium", 750_000L);
        upstreamParams.put("min_size", 100);
        upstreamParams.put("min_volume", 100);
        upstreamParams.put("min_marketcap", 1_000_000_000L);
        upstreamParams.put("max_marketcap", 5_000_000_000L);
        upstreamParams.put("min_dte", 1);
        upstreamParams.put("max_dte", 30);
        upstreamParams.put("expiration", List.of("2027-06-17", "2027-07-16"));
        upstreamParams.put("is_put", true);
        upstreamParams.put("is_sweep", false);
        upstreamParams.put("is_floor", false);
        upstreamParams.put("regular", false);
        upstreamParams.put("bid", false);
        upstreamParams.put("mid", false);
        upstreamParams.put("ask", false);
        upstreamParams.put("noSide", false);
        upstreamParams.put("aboveAsk", false);
        upstreamParams.put("belowBid", false);
        upstreamParams.put("is_otm", true);
        upstreamParams.put("size_greater_oi", true);
        upstreamParams.put("vol_greater_oi", true);
        upstreamParams.put("is_multi_leg", false);
        upstreamParams.put("all_opening", true);
        upstreamParams.put("older_than", olderThan.toString());
        upstreamParams.put("limit", 1);
        when(unusualWhalesQueryParamMapper.toParams(any(Record.class))).thenReturn(upstreamParams);
        when(unusualWhalesClient.getFlowAlertsUncached(upstreamParams)).thenReturn(upstreamResponse);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/option-trades/flow-alerts")
                .param("ticker_symbol", "spy, qqq")
                .param("min_premium", "50000")
                .param("max_premium", "750000")
                .param("min_size", "100")
                .param("min_volume", "100")
                .param("min_marketcap", "1000000000")
                .param("max_marketcap", "5000000000")
                .param("min_dte", "1")
                .param("max_dte", "30")
                .param("expiration", "2027-06-17")
                .param("expiration", "2027-07-16")
                .param("is_put", "true")
                .param("is_sweep", "false")
                .param("is_floor", "false")
                .param("regular", "false")
                .param("bid", "false")
                .param("mid", "false")
                .param("ask", "false")
                .param("noSide", "false")
                .param("aboveAsk", "false")
                .param("belowBid", "false")
                .param("is_otm", "true")
                .param("size_greater_oi", "true")
                .param("vol_greater_oi", "true")
                .param("is_multi_leg", "false")
                .param("all_opening", "true")
                .param("older_than", olderThan.toString())
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value("uw-flow-1"))
            .andExpect(jsonPath("$.data[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$.data[0].option_chain").value("AAPL270617C00150000"))
            .andExpect(jsonPath("$.newerThan").value("2026-06-30T16:14:44.001Z"))
            .andExpect(jsonPath("$.olderThan").value("2026-06-30T16:14:44.001Z"));

        verify(unusualWhalesQueryParamMapper).toParams(any(Record.class));
        verify(unusualWhalesClient).getFlowAlertsUncached(upstreamParams);
        verify(unusualWhalesClient, never()).getFlowAlerts(upstreamParams);
        verifyNoInteractions(flowAlertService);
    }

    @Test
    void shouldPassThroughScreenerOptionContractsQueryParams() {
        MultiValueMap<String, String> requestParams = new LinkedMultiValueMap<>();
        requestParams.add("ticker_symbol", "SPY");
        requestParams.add("is_otm", "true");
        requestParams.add("max_multileg_volume_ratio", "0.5");
        requestParams.add("min_premium", "1000000");
        requestParams.add("vol_greater_oi", "true");
        requestParams.add("limit", "200");
        requestParams.add("order", "volume");
        requestParams.add("min_skew_perc", "0.65");
        requestParams.add("order_direction", "desc");

        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("ticker_symbol", "SPY");
        mapped.put("is_otm", "true");
        mapped.put("max_multileg_volume_ratio", "0.5");
        mapped.put("min_premium", "1000000");
        mapped.put("vol_greater_oi", "true");
        mapped.put("limit", "200");
        mapped.put("order", "volume");
        mapped.put("min_skew_perc", "0.65");
        mapped.put("order_direction", "desc");

        OptionContractsResponse upstream = new OptionContractsResponse(List.of());
        when(unusualWhalesQueryParamMapper.toParams(org.mockito.ArgumentMatchers.<MultiValueMap<String, String>>any()))
            .thenReturn(mapped);
        when(unusualWhalesClient.getOptionContracts(mapped)).thenReturn(upstream);

        ResponseEntity<OptionContractsResponse> response = controller.getOptionContracts(requestParams);

        verify(unusualWhalesQueryParamMapper).toParams(org.mockito.ArgumentMatchers.<MultiValueMap<String, String>>argThat(params ->
            "SPY".equals(params.getFirst("ticker_symbol"))
                && "true".equals(params.getFirst("is_otm"))
                && "0.5".equals(params.getFirst("max_multileg_volume_ratio"))
                && "1000000".equals(params.getFirst("min_premium"))
                && "true".equals(params.getFirst("vol_greater_oi"))
                && "200".equals(params.getFirst("limit"))
                && "volume".equals(params.getFirst("order"))
                && "0.65".equals(params.getFirst("min_skew_perc"))
                && "desc".equals(params.getFirst("order_direction"))
        ));
        verify(unusualWhalesClient).getOptionContracts(mapped);
        assertThat(response.getBody()).isEqualTo(upstream);
    }

    private ResponseEntity<OptionTradePageResponse> invokeBasicQuery(
        int page,
        int pageSize,
        String tickers,
        BigDecimal minPremium,
        Long minVolume,
        Boolean hasSingleleg,
        Boolean hasMultileg,
        String countMode
    ) {
        return controller.getOptionTrades(
            page,
            pageSize,
            tickers,
            minPremium,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            minVolume,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            hasSingleleg,
            hasMultileg,
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
            "sip_timestamp",
            countMode
        );
    }

    private ResponseEntity<OptionTradePageResponse> invokeSortedQuery(String sortBy) {
        return controller.getOptionTrades(
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

    private ResponseEntity<OptionTradePageResponse> invokeExpandedQuery(Instant since, Instant until) {
        return controller.getOptionTrades(
            2,
            1000,
            "spy, qqq",
            new BigDecimal("100000.00"),
            true,  // sweepOnly
            null,  // floorOnly
            "ask_side",
            since,
            until,
            "iwm, dia",
            new BigDecimal("250000.00"),
            25L,   // minSize
            250L,  // maxSize
            100L,  // minVolume
            5000L, // maxVolume
            "2026-03-20, 2026-03-21",
            1,     // minDte
            30,    // maxDte
            false, // calls
            true,  // puts
            false, // sweeps
            null,  // floor
            false, // hasSingleleg
            null,  // hasMultileg
            false, // cross
            false, // regular
            false, // bid
            false, // mid
            false, // ask
            false, // noSide
            null,  // aboveAsk
            false, // belowBid
            true,  // volumeGtOi
            true,  // sizeGtOi
            true,  // excludeDeepItm
            true,  // otmOnly
            null,  // itmOnly
            true,  // hideExpired
            "sip_timestamp",
            "slice"
        );
    }

    private ResponseEntity<OptionTradePageResponse> invokeUnfilteredQuery(String countMode) {
        return controller.getOptionTrades(
            1,
            100,
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
            "sip_timestamp",
            countMode
        );
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

    private static OptionTradeDto tradeDto() {
        return new OptionTradeDto(
            UUID.randomUUID(),
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
            Instant.parse("2026-03-18T14:02:00Z"),
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

    private static UnusualWhalesFlowAlertDto upstreamFlowAlert(Instant createdAt) {
        return new UnusualWhalesFlowAlertDto(
            "uw-flow-1",
            "AAPL",
            "AAPL270617C00150000",
            new BigDecimal("150.00"),
            LocalDate.of(2027, 6, 17),
            "CALL",
            new BigDecimal("15.05"),
            new BigDecimal("15.00"),
            new BigDecimal("15.10"),
            new BigDecimal("201.25"),
            2000,
            new BigDecimal("3010000.00"),
            1,
            2233,
            450,
            new BigDecimal("4.96"),
            null,
            null,
            "FloorTradeLargeCap",
            false,
            true,
            true,
            false,
            true,
            "Technology",
            new BigDecimal("3000000000000"),
            null,
            1_782_836_070_957L,
            1_782_836_070_957L,
            new BigDecimal("3010000.00"),
            BigDecimal.ZERO,
            createdAt,
            "rule-1",
            1,
            null,
            "Common Stock",
            false
        );
    }

    private record PageResponseFixture(OptionTradeDto dto) {

        private OptionTradeQueryResult sliceResult() {
            return new OptionTradeQueryResult(List.of(dto), null, true, false, OptionTradeCountMode.SLICE);
        }
    }
}
