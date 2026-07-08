package com.marketmono.service;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.entity.DpLpTrade;
import com.marketmono.entity.VenueType;
import com.marketmono.repository.DpLpTradeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Ingestion service for off-lit (dark pool) trades received via the market data WebSocket.
 *
 * <p>Each incoming trade is:
 * <ol>
 *   <li>Broadcast immediately to STOMP clients via {@link OffLitTradeBroadcaster}</li>
 *   <li>Queued into a bounded buffer for batched persistence to {@code dp_lp_trades}</li>
 * </ol>
 *
 * <p>The flush runs every 2 seconds (configurable). Duplicate tracking_ids are
 * handled at the DB level via the composite PK.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OffLitTradeIngestionService {

    private static final int QUEUE_CAPACITY = 5000;
    private static final int MAX_BATCH_SIZE = 200;

    private final UnusualWhalesWebSocketClient webSocketClient;
    private final UnusualWhalesConfig config;
    private final OffLitTradeBroadcaster broadcaster;
    private final DpLpTradeRepository tradeRepository;

    private final BlockingQueue<DarkPoolRecentTradeDto> buffer = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @PostConstruct
    public void initialize() {
        if (config.getWebsocket().isEnabled()) {
            log.info("[OFF-LIT] Registering off_lit_trades WebSocket handler");
            webSocketClient.onOffLitTrade(this::handleOffLitTrade);
        }
    }

    /**
     * Handle a single off-lit trade from the market data WebSocket.
     * Broadcasts immediately and queues for batch persistence.
     */
    private void handleOffLitTrade(DarkPoolRecentTradeDto dto) {
        try {
            // Broadcast immediately for real-time frontend updates
            broadcaster.broadcastTrade(dto);

            // Queue for batched DB persistence
            if (!buffer.offer(dto)) {
                log.warn("[OFF-LIT] Queue full, dropping trade: {} ticker={}", dto.trackingId(), dto.ticker());
            }
        } catch (Exception e) {
            log.error("[OFF-LIT] Error handling trade {}: {}", dto.trackingId(), e.getMessage(), e);
        }
    }

    /**
     * Flush queued trades to the database in batches.
     * Runs on a fixed schedule (every 2 seconds).
     */
    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(name = "offLitTrades_flushBatch", lockAtMostFor = "PT2M", lockAtLeastFor = "PT1S")
    @Transactional
    public void flushBatch() {
        if (buffer.isEmpty()) return;

        List<DarkPoolRecentTradeDto> batch = new ArrayList<>(MAX_BATCH_SIZE);
        buffer.drainTo(batch, MAX_BATCH_SIZE);
        if (batch.isEmpty()) return;

        try {
            List<DpLpTrade> entities = batch.stream()
                .map(OffLitTradeIngestionService::toEntity)
                .toList();

            tradeRepository.saveAll(entities);
            log.debug("[OFF-LIT] Persisted {} trades to dp_lp_trades", entities.size());
        } catch (Exception e) {
            log.error("[OFF-LIT] Failed to persist batch of {} trades: {}", batch.size(), e.getMessage(), e);
        }
    }

    /**
     * Map an upstream dark pool trade DTO to a DpLpTrade entity.
     */
    static DpLpTrade toEntity(DarkPoolRecentTradeDto dto) {
        OffsetDateTime ts;
        try {
            ts = OffsetDateTime.parse(dto.executedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            ts = OffsetDateTime.now();
        }

        return DpLpTrade.builder()
            .id(dto.trackingId())
            .ticker(dto.ticker())
            .venueType(VenueType.DARK)
            .price(dto.price() != null ? dto.price() : BigDecimal.ZERO)
            .size(dto.size() != null ? dto.size() : 0)
            .ts(ts)
            .venue(dto.marketCenter())
            .ingestSource("OPTIONS_FEED_WS")
            .isNewEntity(true)
            .build();
    }
}
