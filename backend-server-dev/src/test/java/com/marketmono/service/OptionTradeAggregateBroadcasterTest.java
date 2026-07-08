package com.marketmono.service;

import com.marketmono.dto.OptionTradeAggregateDto;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.entity.OptionTradeAggregate;
import com.marketmono.util.DateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionTradeAggregateBroadcasterTest {

    @Mock
    private WebSocketRelayPublisher webSocketRelayPublisher;

    @Mock
    private OptionTradeAggregateService aggregateService;

    @Mock
    private RecentRedisBufferService recentRedisBufferService;

    @Mock
    private HotOptionTradeAggregateCacheService hotAggregateCacheService;

    @Mock
    private OptionTradeMarketDayResolver marketDayResolver;

    @InjectMocks
    private OptionTradeAggregateBroadcaster broadcaster;

    @Test
    void shouldHydrateVolumesBeforeBufferingAndBroadcasting() {
        UUID aggregateId = UUID.randomUUID();
        OptionTradeAggregate aggregate = OptionTradeAggregate.builder()
            .aggregateId(aggregateId)
            .groupingVersion(1)
            .ticker("SPY")
            .optionChain("SPY_260320_C_510")
            .side("ASK_SIDE")
            .aggressor("ASK_SIDE")
            .conditionSignature("AUTO")
            .isSweep(true)
            .isFloorTrade(false)
            .isMultileg(false)
            .firstExecutedAt(Instant.parse("2026-03-18T14:00:00Z"))
            .lastExecutedAt(Instant.parse("2026-03-18T14:00:01Z"))
            .representativePrice(new BigDecimal("1.0100"))
            .minPrice(new BigDecimal("1.0000"))
            .maxPrice(new BigDecimal("1.0200"))
            .totalSize(30L)
            .totalPremium(new BigDecimal("3000.00"))
            .tradeCount(2)
            .exchangeCount(2)
            .expiry(LocalDate.of(2026, 3, 20))
            .optionType("call")
            .strike(new BigDecimal("510.00"))
            .underlyingPriceFirst(new BigDecimal("500.00"))
            .underlyingPriceLast(new BigDecimal("500.05"))
            .openInterest(1500)
            .volume(null)
            .build();

        OptionTradeAggregateDto rawDto = OptionTradeAggregateDto.from(aggregate);
        OptionTradeAggregateDto hydratedDto = new OptionTradeAggregateDto(
            aggregateId,
            1,
            "SPY",
            "SPY_260320_C_510",
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
            2400L
        );

        when(aggregateService.hydrateVolumes(List.of(rawDto))).thenReturn(List.of(hydratedDto));
        when(recentRedisBufferService.isEnabled()).thenReturn(false);

        broadcaster.broadcastAggregates(List.of(aggregate));

        verify(aggregateService).hydrateVolumes(List.of(rawDto));
        verify(recentRedisBufferService).appendAll(
            eq("option-trade-aggregates:buffer"),
            eq(List.of(hydratedDto)),
            any(),
            any()
        );
        verify(webSocketRelayPublisher).publish("/topic/option-trade-aggregates", List.of(hydratedDto));
        verify(webSocketRelayPublisher).publish("/topic/option-trade-aggregates/SPY", List.of(hydratedDto));
        assertThat(broadcaster.getRecentAggregates(10)).containsExactly(hydratedDto);
    }

    @Test
    void shouldReturnBoundedLocalFallbackSnapshotNewestFirstAndDefensive() {
        OptionTradeAggregateDto first = aggregateDto("AAA", Instant.parse("2026-03-18T14:00:00Z"));
        OptionTradeAggregateDto second = aggregateDto("BBB", Instant.parse("2026-03-18T14:00:01Z"));
        OptionTradeAggregateDto third = aggregateDto("CCC", Instant.parse("2026-03-18T14:00:02Z"));

        when(aggregateService.hydrateVolumes(List.of(first))).thenReturn(List.of(first));
        when(aggregateService.hydrateVolumes(List.of(second))).thenReturn(List.of(second));
        when(aggregateService.hydrateVolumes(List.of(third))).thenReturn(List.of(third));
        when(recentRedisBufferService.isEnabled()).thenReturn(false);

        broadcaster.broadcastAggregates(List.of(aggregate("AAA", first.lastExecutedAt())));
        broadcaster.broadcastAggregates(List.of(aggregate("BBB", second.lastExecutedAt())));
        broadcaster.broadcastAggregates(List.of(aggregate("CCC", third.lastExecutedAt())));

        List<OptionTradeAggregateDto> recent = broadcaster.getRecentAggregates(2);

        assertThat(recent)
            .extracting(OptionTradeAggregateDto::ticker)
            .containsExactly("CCC", "BBB");
        assertThatCode(() -> recent.add(first)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(broadcaster.getBufferSize()).isEqualTo(3);
    }

    @Test
    void shouldRefreshDefaultFlowPresetWithResolvedDayAndSliceResponse() {
        OptionTradeAggregate aggregate = aggregate("SPY", Instant.parse("2026-05-15T14:00:00Z"));
        OptionTradeAggregateDto dto = OptionTradeAggregateDto.from(aggregate);
        LocalDate latestRawDay = LocalDate.of(2026, 5, 15);
        OptionTradeMarketDayResolver.ResolvedMarketDay resolvedMarketDay =
            new OptionTradeMarketDayResolver.ResolvedMarketDay(
                latestRawDay,
                DateUtils.startOfEasternDay(latestRawDay),
                DateUtils.endOfEasternDay(latestRawDay),
                OptionTradeMarketDayResolver.SOURCE_RAW,
                latestRawDay,
                latestRawDay
            );
        OptionTradeAggregateService.OptionTradeAggregateFilter filter =
            new OptionTradeAggregateService.OptionTradeAggregateFilter(
                null,
                null,
                resolvedMarketDay.since(),
                resolvedMarketDay.until(),
                null,
                null,
                null,
                HotOptionTradeAggregateCacheService.defaultFlowPremium(),
                null,
                null,
                null,
                HotOptionTradeAggregateCacheService.defaultFlowVolume(),
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
                null
            );
        PageRequest pageable = PageRequest.of(0, 150);
        HotOptionTradeAggregateCacheService.HotAggregatePageKey key =
            new HotOptionTradeAggregateCacheService.HotAggregatePageKey(
                1,
                150,
                HotOptionTradeAggregateCacheService.defaultFlowPremium(),
                latestRawDay,
                HotOptionTradeAggregateCacheService.defaultFlowFilterIdentity(),
                OptionTradeCountMode.SLICE,
                "sip_timestamp"
            );

        when(aggregateService.hydrateVolumes(List.of(dto))).thenReturn(List.of(dto));
        when(hotAggregateCacheService.shouldRefreshDefaultFlowPreset()).thenReturn(true);
        when(marketDayResolver.resolveAggregateDefaultDay()).thenReturn(Optional.of(resolvedMarketDay));
        when(hotAggregateCacheService.defaultFlowFilter(resolvedMarketDay)).thenReturn(filter);
        when(hotAggregateCacheService.defaultFlowPageable()).thenReturn(pageable);
        when(hotAggregateCacheService.defaultFlowPresetKey(resolvedMarketDay)).thenReturn(key);
        when(aggregateService.getAggregates(filter, pageable, OptionTradeCountMode.SLICE))
            .thenReturn(OptionTradeAggregateService.OptionTradeAggregateQueryResult.slice(List.of(dto), false));

        broadcaster.broadcastAggregates(List.of(aggregate));

        verify(aggregateService).getAggregates(filter, pageable, OptionTradeCountMode.SLICE);
        verify(hotAggregateCacheService).put(eq(key), argThat(response ->
            response != null
                && response.countMode() == OptionTradeCountMode.SLICE
                && !response.totalCountExact()
                && response.totalCount() == null
                && response.results().equals(List.of(dto))
        ));
    }

    private OptionTradeAggregate aggregate(String ticker, Instant lastExecutedAt) {
        return OptionTradeAggregate.builder()
            .aggregateId(UUID.nameUUIDFromBytes(ticker.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .groupingVersion(1)
            .ticker(ticker)
            .optionChain(ticker + "_260320_C_510")
            .side("ASK_SIDE")
            .aggressor("ASK_SIDE")
            .conditionSignature("AUTO")
            .isSweep(true)
            .isFloorTrade(false)
            .isMultileg(false)
            .firstExecutedAt(lastExecutedAt.minusSeconds(1))
            .lastExecutedAt(lastExecutedAt)
            .representativePrice(new BigDecimal("1.0100"))
            .minPrice(new BigDecimal("1.0000"))
            .maxPrice(new BigDecimal("1.0200"))
            .totalSize(30L)
            .totalPremium(new BigDecimal("3000.00"))
            .tradeCount(2)
            .exchangeCount(2)
            .expiry(LocalDate.of(2026, 3, 20))
            .optionType("call")
            .strike(new BigDecimal("510.00"))
            .underlyingPriceFirst(new BigDecimal("500.00"))
            .underlyingPriceLast(new BigDecimal("500.05"))
            .openInterest(1500)
            .volume(2400L)
            .build();
    }

    private OptionTradeAggregateDto aggregateDto(String ticker, Instant lastExecutedAt) {
        return OptionTradeAggregateDto.from(aggregate(ticker, lastExecutedAt));
    }
}
