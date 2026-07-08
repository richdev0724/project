package com.marketmono.service;

import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.service.support.LocalRecentBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Async broadcaster for off-lit (dark pool) trades via STOMP WebSocket.
 *
 * <p>Maintains an in-memory circular buffer of the most recent trades for
 * fast retrieval on page load (no DB hit). The buffer holds up to
 * {@value #BUFFER_CAPACITY} trades and is thread-safe.
 *
 * <p>Broadcasts to:
 * <ul>
 *   <li>{@code /topic/off-lit-trades} — all dark pool trades (batch of DTOs)</li>
 *   <li>{@code /topic/off-lit-trades/{ticker}} — per-ticker trades for filtered views</li>
 * </ul>
 *
 * <p>Same pattern as {@link OptionTradeBroadcaster}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OffLitTradeBroadcaster {

    private static final int BUFFER_CAPACITY = 500;
    private static final String REDIS_BUFFER_KEY = "off-lit-trades:buffer";

    private final WebSocketRelayPublisher webSocketRelayPublisher;
    private final RecentRedisBufferService recentRedisBufferService;

    private final LocalRecentBuffer<DarkPoolRecentTradeDto> buffer = new LocalRecentBuffer<>(BUFFER_CAPACITY);

    /**
     * Broadcast a batch of off-lit trades to WebSocket subscribers and
     * append them to the in-memory circular buffer.
     */
    @Async
    public void broadcastTrades(List<DarkPoolRecentTradeDto> trades) {
        if (trades.isEmpty()) return;

        try {
            appendToBuffer(trades);
            recentRedisBufferService.appendAll(
                REDIS_BUFFER_KEY,
                trades,
                trade -> String.valueOf(trade.trackingId()),
                OffLitTradeBroadcaster::parseExecutedAt
            );

            // Broadcast full batch to all-trades topic
            webSocketRelayPublisher.publish("/topic/off-lit-trades", trades);

            // Group by ticker and broadcast to per-ticker topics
            Map<String, List<DarkPoolRecentTradeDto>> byTicker = trades.stream()
                .collect(Collectors.groupingBy(DarkPoolRecentTradeDto::ticker));

            for (var entry : byTicker.entrySet()) {
                webSocketRelayPublisher.publish(
                    "/topic/off-lit-trades/" + entry.getKey(),
                    entry.getValue()
                );
            }

            log.debug("Broadcast {} off-lit trades to /topic/off-lit-trades ({} tickers)",
                trades.size(), byTicker.size());
        } catch (Exception e) {
            log.warn("Failed to broadcast off-lit trades via WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * Broadcast a single off-lit trade (called per message from the market data WebSocket).
     */
    @Async
    public void broadcastTrade(DarkPoolRecentTradeDto trade) {
        broadcastTrades(List.of(trade));
    }

    /**
     * Returns the most recent trades from the in-memory buffer.
     *
     * @param limit max number of trades to return (capped at buffer capacity)
     * @return list of recent trades, newest last
     */
    public List<DarkPoolRecentTradeDto> getRecentTrades(int limit) {
        int capped = Math.min(Math.max(limit, 1), BUFFER_CAPACITY);

        if (recentRedisBufferService.isEnabled()) {
            List<DarkPoolRecentTradeDto> recent = recentRedisBufferService.getRecent(REDIS_BUFFER_KEY, capped, DarkPoolRecentTradeDto.class);
            if (!recent.isEmpty()) {
                return recent;
            }
        }

        return buffer.snapshot(capped);
    }

    public int getBufferSize() {
        if (recentRedisBufferService.isEnabled()) {
            int redisSize = recentRedisBufferService.size(REDIS_BUFFER_KEY);
            if (redisSize > 0) {
                return redisSize;
            }
        }

        return buffer.getBufferSize();
    }

    private void appendToBuffer(List<DarkPoolRecentTradeDto> dtos) {
        buffer.appendAll(dtos);
    }

    private static Instant parseExecutedAt(DarkPoolRecentTradeDto trade) {
        try {
            return OffsetDateTime.parse(trade.executedAt()).toInstant();
        } catch (Exception ignored) {
            return Instant.now();
        }
    }
}
