package com.marketmono.service;

import com.marketmono.dto.OptionTradeDto;
import com.marketmono.entity.OptionTrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionTradeBroadcasterTest {

    @Mock
    private WebSocketRelayPublisher webSocketRelayPublisher;

    @Mock
    private RecentRedisBufferService recentRedisBufferService;

    @InjectMocks
    private OptionTradeBroadcaster broadcaster;

    @Test
    void shouldBroadcastOnlyHighPremiumSingleLegTrades() {
        OptionTrade eligibleTrade = trade("SPY", "60000.00", false, Instant.parse("2026-04-17T14:00:00Z"));
        OptionTrade lowPremiumTrade = trade("QQQ", "49999.99", false, Instant.parse("2026-04-17T14:00:01Z"));
        OptionTrade multilegTrade = trade("IWM", "90000.00", true, Instant.parse("2026-04-17T14:00:02Z"));

        broadcaster.broadcastTrades(List.of(eligibleTrade, lowPremiumTrade, multilegTrade));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OptionTradeDto>> publishedBatchCaptor = ArgumentCaptor.forClass(List.class);
        verify(webSocketRelayPublisher).publish(eq("/topic/option-trades"), publishedBatchCaptor.capture());
        verify(webSocketRelayPublisher).publish(eq("/topic/option-trades/SPY"), any());
        verify(webSocketRelayPublisher, never()).publish(eq("/topic/option-trades/QQQ"), any());
        verify(webSocketRelayPublisher, never()).publish(eq("/topic/option-trades/IWM"), any());

        List<OptionTradeDto> publishedBatch = publishedBatchCaptor.getValue();
        assertThat(publishedBatch).hasSize(1);
        assertThat(publishedBatch.getFirst().ticker()).isEqualTo("SPY");
        assertThat(publishedBatch.getFirst().premium()).isEqualByComparingTo("60000.00");
        assertThat(publishedBatch.getFirst().isMultileg()).isFalse();

        assertThat(broadcaster.getRecentTrades(10))
            .extracting(OptionTradeDto::ticker)
            .containsExactly("SPY");
    }

    @Test
    void shouldFilterRecentTradesReadFromRedisBuffer() {
        when(recentRedisBufferService.isEnabled()).thenReturn(true);
        when(recentRedisBufferService.getRecent("option-trades:buffer", 10, OptionTradeDto.class)).thenReturn(List.of(
            dto("SPY", "60000.00", false, Instant.parse("2026-04-17T14:00:00Z")),
            dto("QQQ", "40000.00", false, Instant.parse("2026-04-17T14:00:01Z")),
            dto("IWM", "90000.00", true, Instant.parse("2026-04-17T14:00:02Z"))
        ));

        List<OptionTradeDto> recent = broadcaster.getRecentTrades(10);

        verify(recentRedisBufferService).getRecent("option-trades:buffer", 10, OptionTradeDto.class);

        assertThat(recent).hasSize(1);
        assertThat(recent.getFirst().ticker()).isEqualTo("SPY");
        assertThat(recent.getFirst().premium()).isEqualByComparingTo("60000.00");
    }

    @Test
    void shouldReturnBoundedFilteredRecentTradesNewestFirstFromRedisBuffer() {
        when(recentRedisBufferService.isEnabled()).thenReturn(true);
        when(recentRedisBufferService.getRecent("option-trades:buffer", 2, OptionTradeDto.class)).thenReturn(List.of(
            dto("OLD", "70000.00", false, Instant.parse("2026-04-17T14:00:00Z"), "AUTO"),
            dto("OSEQ", "80000.00", false, Instant.parse("2026-04-17T14:00:01Z"), "OSEQ|intermarket_sweep"),
            dto("MID", "90000.00", false, Instant.parse("2026-04-17T14:00:02Z"), "AUTO"),
            dto("LOW", "40000.00", false, Instant.parse("2026-04-17T14:00:03Z"), "AUTO"),
            dto("NEW", "100000.00", false, Instant.parse("2026-04-17T14:00:04Z"), "AUTO")
        ));

        List<OptionTradeDto> recent = broadcaster.getRecentTrades(2);

        assertThat(recent)
            .extracting(OptionTradeDto::ticker)
            .containsExactly("NEW", "MID");
        assertThat(recent).hasSize(2);
    }

    @Test
    void shouldScanLocalFallbackBufferPastRejectedRecentTradesUntilLimitIsSatisfied() {
        broadcaster.broadcastTrades(List.of(
            trade("OLD", "70000.00", false, Instant.parse("2026-04-17T14:00:00Z")),
            trade("OSEQ", "80000.00", false, Instant.parse("2026-04-17T14:00:01Z"), "OSEQ|intermarket_sweep"),
            trade("MID", "90000.00", false, Instant.parse("2026-04-17T14:00:02Z")),
            trade("LOW", "40000.00", false, Instant.parse("2026-04-17T14:00:03Z")),
            trade("NEW", "100000.00", false, Instant.parse("2026-04-17T14:00:04Z"))
        ));

        List<OptionTradeDto> recent = broadcaster.getRecentTrades(2);

        assertThat(recent)
            .extracting(OptionTradeDto::ticker)
            .containsExactly("NEW", "MID");
    }

    @Test
    void shouldNotBroadcastTradesWithExcludedPriorityTradeCodes() {
        OptionTrade outOfSequenceTrade = trade(
            "SPY",
            "60000.00",
            false,
            Instant.parse("2026-04-17T14:00:00Z"),
            "OSEQ|intermarket_sweep"
        );

        broadcaster.broadcastTrades(List.of(outOfSequenceTrade));

        verify(webSocketRelayPublisher, never()).publish(eq("/topic/option-trades"), any());
        assertThat(broadcaster.getRecentTrades(10)).isEmpty();
    }

    private OptionTrade trade(String ticker, String premium, boolean isMultileg, Instant executedAt) {
        return trade(ticker, premium, isMultileg, executedAt, "AUTO");
    }

    private OptionTrade trade(
        String ticker,
        String premium,
        boolean isMultileg,
        Instant executedAt,
        String condition
    ) {
        return OptionTrade.builder()
            .tradeId(UUID.randomUUID())
            .ticker(ticker)
            .optionChain(ticker + "_260417_C_500")
            .price(new BigDecimal("1.00"))
            .size(10)
            .premium(new BigDecimal(premium))
            .underlyingPrice(new BigDecimal("500.00"))
            .nbboBid(new BigDecimal("0.95"))
            .nbboAsk(new BigDecimal("1.05"))
            .exchange("CBOE")
            .executedAt(executedAt)
            .condition(condition)
            .aggressor("ASK_SIDE")
            .side("ASK_SIDE")
            .isSweep(false)
            .isFloorTrade(false)
            .isMultileg(isMultileg)
            .expiry(LocalDate.of(2026, 4, 17))
            .optionType("call")
            .openInterest(1000)
            .strike(new BigDecimal("500.00"))
            .volume(2500L)
            .build();
    }

    private OptionTradeDto dto(String ticker, String premium, boolean isMultileg, Instant executedAt) {
        return dto(ticker, premium, isMultileg, executedAt, "AUTO");
    }

    private OptionTradeDto dto(
        String ticker,
        String premium,
        boolean isMultileg,
        Instant executedAt,
        String condition
    ) {
        return new OptionTradeDto(
            UUID.randomUUID(),
            ticker,
            ticker + "_260417_C_500",
            new BigDecimal("1.00"),
            10,
            new BigDecimal(premium),
            new BigDecimal("500.00"),
            new BigDecimal("0.95"),
            new BigDecimal("1.05"),
            "CBOE",
            "ASK_SIDE",
            condition,
            "ASK_SIDE",
            false,
            false,
            isMultileg,
            executedAt,
            LocalDate.of(2026, 4, 17),
            "call",
            1000,
            new BigDecimal("500.00"),
            2500L,
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
}
