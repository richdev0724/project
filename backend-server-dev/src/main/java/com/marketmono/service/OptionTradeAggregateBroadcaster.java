package com.marketmono.service;

import com.marketmono.dto.OptionTradeAggregateDto;
import com.marketmono.dto.OptionTradeAggregatePageResponse;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.entity.OptionTradeAggregate;
import com.marketmono.service.support.LocalRecentBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OptionTradeAggregateBroadcaster {

    private static final int BUFFER_CAPACITY = 2000;
    private static final String REDIS_BUFFER_KEY = "option-trade-aggregates:buffer";

    private final WebSocketRelayPublisher webSocketRelayPublisher;
    private final OptionTradeAggregateService aggregateService;
    private final RecentRedisBufferService recentRedisBufferService;
    private final HotOptionTradeAggregateCacheService hotAggregateCacheService;
    private final OptionTradeMarketDayResolver marketDayResolver;

    private final LocalRecentBuffer<OptionTradeAggregateDto> buffer = new LocalRecentBuffer<>(BUFFER_CAPACITY);

    public void broadcastAggregates(List<OptionTradeAggregate> aggregates) {
        if (aggregates == null || aggregates.isEmpty()) {
            return;
        }

        try {
            List<OptionTradeAggregateDto> dtos = aggregateService.hydrateVolumes(aggregates.stream()
                .map(OptionTradeAggregateDto::from)
                .toList());

            appendToBuffer(dtos);
            recentRedisBufferService.appendAll(
                REDIS_BUFFER_KEY,
                dtos,
                dto -> dto.aggregateId().toString(),
                OptionTradeAggregateDto::lastExecutedAt
            );
            webSocketRelayPublisher.publish("/topic/option-trade-aggregates", dtos);

            Map<String, List<OptionTradeAggregateDto>> byTicker = dtos.stream()
                .collect(Collectors.groupingBy(OptionTradeAggregateDto::ticker));

            for (var entry : byTicker.entrySet()) {
                webSocketRelayPublisher.publish(
                    "/topic/option-trade-aggregates/" + entry.getKey(),
                    entry.getValue()
                );
            }

            refreshDefaultFlowPresetIfNeeded();

            log.debug("[KAFKA] Broadcast {} aggregate option trades ({} tickers)", dtos.size(), byTicker.size());
        } catch (Exception e) {
            log.warn("[KAFKA] Failed to broadcast aggregate option trades via WebSocket: {}", e.getMessage(), e);
        }
    }

    public List<OptionTradeAggregateDto> getRecentAggregates(int limit) {
        int capped = Math.min(Math.max(limit, 1), BUFFER_CAPACITY);

        if (recentRedisBufferService.isEnabled()) {
            List<OptionTradeAggregateDto> recent = recentRedisBufferService.getRecent(
                REDIS_BUFFER_KEY,
                capped,
                OptionTradeAggregateDto.class
            );
            if (!recent.isEmpty()) {
                return latestAggregatesFirst(recent, capped);
            }
        }

        return latestAggregatesFirst(buffer.snapshot(capped), capped);
    }

    private void appendToBuffer(List<OptionTradeAggregateDto> dtos) {
        buffer.appendAll(dtos);
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

    private List<OptionTradeAggregateDto> latestAggregatesFirst(List<OptionTradeAggregateDto> source, int limit) {
        if (source.isEmpty()) {
            return List.of();
        }

        List<OptionTradeAggregateDto> recent = new ArrayList<>(Math.min(source.size(), limit));
        for (int index = source.size() - 1; index >= 0 && recent.size() < limit; index--) {
            recent.add(source.get(index));
        }

        return List.copyOf(recent);
    }

    private void refreshDefaultFlowPresetIfNeeded() {
        if (!hotAggregateCacheService.shouldRefreshDefaultFlowPreset()) {
            return;
        }

        try {
            OptionTradeMarketDayResolver.ResolvedMarketDay resolvedDefaultDay =
                marketDayResolver.resolveAggregateDefaultDay().orElse(null);
            var result = aggregateService.getAggregates(
                hotAggregateCacheService.defaultFlowFilter(resolvedDefaultDay),
                hotAggregateCacheService.defaultFlowPageable(),
                OptionTradeCountMode.SLICE
            );
            hotAggregateCacheService.put(
                hotAggregateCacheService.defaultFlowPresetKey(resolvedDefaultDay),
                new OptionTradeAggregatePageResponse(
                    List.copyOf(result.results()),
                    result.totalCount(),
                    result.hasNext(),
                    result.totalCountExact(),
                    result.countMode()
                )
            );
        } catch (RuntimeException exception) {
            hotAggregateCacheService.markDefaultFlowPresetRefreshFailed();
            log.warn("[KAFKA] Failed to refresh default Flow aggregate preset cache: {}", exception.getMessage());
        }
    }
}
