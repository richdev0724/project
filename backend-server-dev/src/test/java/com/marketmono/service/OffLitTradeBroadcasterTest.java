package com.marketmono.service;

import com.marketmono.dto.DarkPoolRecentTradeDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffLitTradeBroadcasterTest {

    @Mock
    private WebSocketRelayPublisher webSocketRelayPublisher;

    @Mock
    private RecentRedisBufferService recentRedisBufferService;

    @Test
    void shouldReturnBoundedLocalFallbackSnapshotInAppendOrderAndDefensive() {
        OffLitTradeBroadcaster broadcaster = new OffLitTradeBroadcaster(
            webSocketRelayPublisher,
            recentRedisBufferService
        );
        when(recentRedisBufferService.isEnabled()).thenReturn(false);

        broadcaster.broadcastTrades(List.of(
            trade("AAA", 1L),
            trade("BBB", 2L),
            trade("CCC", 3L)
        ));

        List<DarkPoolRecentTradeDto> recent = broadcaster.getRecentTrades(2);

        assertThat(recent)
            .extracting(DarkPoolRecentTradeDto::ticker)
            .containsExactly("BBB", "CCC");
        assertThatCode(() -> recent.add(trade("DDD", 4L))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(broadcaster.getBufferSize()).isEqualTo(3);
    }

    private DarkPoolRecentTradeDto trade(String ticker, long trackingId) {
        return new DarkPoolRecentTradeDto(
            ticker,
            new BigDecimal("100.00"),
            10,
            1000,
            "2026-04-17T14:00:00Z",
            new BigDecimal("100000.00"),
            new BigDecimal("100.05"),
            new BigDecimal("99.95"),
            10,
            10,
            "DARK",
            false,
            null,
            null,
            trackingId,
            "AUTO",
            "REGULAR"
        );
    }
}
