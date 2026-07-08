package com.marketmono.service;

import com.marketmono.dto.OptionTradeDto;
import com.marketmono.entity.OptionTrade;
import com.marketmono.service.support.LocalRecentBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Async broadcaster for option trades via STOMP WebSocket.
 *
 * <p>Maintains an in-memory circular buffer of the most recent trades for
 * fast retrieval on page load (no DB hit). The buffer holds up to
 * {@value #BUFFER_CAPACITY} trades and is thread-safe.
 *
 * <p>Broadcasts to:
 * <ul>
 *   <li>{@code /topic/option-trades} — all trades (batch of DTOs)</li>
 *   <li>{@code /topic/option-trades/{ticker}} — per-ticker trades for filtered views</li>
 * </ul>
 *
 * <p>Trades are sent as batches (matching Kafka consumer batch size) to minimize
 * WebSocket message overhead at 1000+ trades/sec throughput.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OptionTradeBroadcaster {

    private static final int BUFFER_CAPACITY = 2000;
    private static final String REDIS_BUFFER_KEY = "option-trades:buffer";
    private static final BigDecimal MIN_LIVE_PREMIUM = BigDecimal.valueOf(50_000);
    private static final long MIN_LIVE_VOLUME = 100L;
    private static final Set<String> LIVE_FILTER_EXCLUDED_TRADE_CODES = Set.of("OSEQ");

    private final WebSocketRelayPublisher webSocketRelayPublisher;
    private final RecentRedisBufferService recentRedisBufferService;

    private final LocalRecentBuffer<OptionTradeDto> buffer = new LocalRecentBuffer<>(BUFFER_CAPACITY);

    /**
     * Broadcasts all trades in the batch to WebSocket subscribers and
     * appends them to the in-memory circular buffer.
     * Called asynchronously from the Kafka consumer after persistence.
     */
    @Async
    public void broadcastTrades(List<OptionTrade> trades) {
        if (trades.isEmpty()) return;

        try {
            List<OptionTradeDto> dtos = trades.stream()
                .filter(this::shouldBroadcastTrade)
                .map(OptionTradeDto::from)
                .toList();

            if (dtos.isEmpty()) {
                return;
            }

            // Append to circular buffer
            appendToBuffer(dtos);
            recentRedisBufferService.appendAll(
                REDIS_BUFFER_KEY,
                dtos,
                dto -> dto.tradeId().toString(),
                OptionTradeDto::executedAt
            );

            // Broadcast full batch to all-trades topic
            webSocketRelayPublisher.publish("/topic/option-trades", dtos);

            // Group by ticker and broadcast to per-ticker topics
            Map<String, List<OptionTradeDto>> byTicker = dtos.stream()
                .collect(Collectors.groupingBy(OptionTradeDto::ticker));

            for (var entry : byTicker.entrySet()) {
                webSocketRelayPublisher.publish(
                    "/topic/option-trades/" + entry.getKey(),
                    entry.getValue()
                );
            }

            log.debug("[KAFKA] Broadcast {} option trades to /topic/option-trades ({} tickers)",
                dtos.size(), byTicker.size());
        } catch (Exception e) {
            log.warn("[KAFKA] Failed to broadcast option trades via WebSocket: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns the most recent trades from the in-memory buffer.
     *
     * @param limit max number of trades to return (capped at buffer capacity)
        * @return list of recent trades, newest first
     */
    public List<OptionTradeDto> getRecentTrades(int limit) {
        int capped = Math.min(Math.max(limit, 1), BUFFER_CAPACITY);

        if (recentRedisBufferService.isEnabled()) {
            List<OptionTradeDto> recent = recentRedisBufferService.getRecent(
                REDIS_BUFFER_KEY,
                capped,
                OptionTradeDto.class
            );
            if (!recent.isEmpty()) {
                return filterRecentTrades(recent, capped);
            }
        }

        return buffer.snapshotNewestFirst(capped, this::shouldExposeTrade);
    }

    /**
     * Returns the current number of trades held in the buffer.
     */
    public int getBufferSize() {
        if (recentRedisBufferService.isEnabled()) {
            int redisSize = recentRedisBufferService.size(REDIS_BUFFER_KEY);
            if (redisSize > 0) {
                return redisSize;
            }
        }

        return buffer.getBufferSize();
    }

    private void appendToBuffer(List<OptionTradeDto> dtos) {
        buffer.appendAll(dtos);
    }

    private boolean shouldBroadcastTrade(OptionTrade trade) {
        return trade != null
            && trade.getPremium() != null
            && trade.getPremium().compareTo(MIN_LIVE_PREMIUM) >= 0
            && trade.getVolume() != null
            && trade.getVolume() >= MIN_LIVE_VOLUME
            && !Boolean.TRUE.equals(trade.getIsMultileg())
            && isLiveEligibleTradeCode(trade.getCondition());
    }

    private boolean shouldExposeTrade(OptionTradeDto trade) {
        return trade != null
            && trade.premium() != null
            && trade.premium().compareTo(MIN_LIVE_PREMIUM) >= 0
            && trade.volume() != null
            && trade.volume() >= MIN_LIVE_VOLUME
            && !Boolean.TRUE.equals(trade.isMultileg())
            && isLiveEligibleTradeCode(trade.condition());
    }

    private boolean isLiveEligibleTradeCode(String conditionSignature) {
        String tradeCode = extractTradeCode(conditionSignature);
        return tradeCode == null || !LIVE_FILTER_EXCLUDED_TRADE_CODES.contains(tradeCode);
    }

    private String extractTradeCode(String conditionSignature) {
        if (conditionSignature == null || conditionSignature.isBlank()) {
            return null;
        }

        String normalized = conditionSignature.trim().toUpperCase(Locale.ROOT);
        int separatorIndex = normalized.indexOf('|');
        String tradeCode = separatorIndex >= 0
            ? normalized.substring(0, separatorIndex)
            : normalized;

        return tradeCode.isBlank() ? null : tradeCode;
    }

    private List<OptionTradeDto> filterRecentTrades(List<OptionTradeDto> source, int limit) {
        if (source.isEmpty()) {
            return List.of();
        }

        List<OptionTradeDto> filtered = new ArrayList<>(Math.min(source.size(), limit));
        for (int index = source.size() - 1; index >= 0 && filtered.size() < limit; index--) {
            OptionTradeDto trade = source.get(index);
            if (shouldExposeTrade(trade)) {
                filtered.add(trade);
            }
        }

        return List.copyOf(filtered);
    }
}
