package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.dto.UnusualWhalesFlowResponse;
import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.repository.FlowAlertRepository;
import com.marketmono.repository.FlowAlertSpecifications;
import com.marketmono.repository.FlowAlertSpecifications.FlowAlertFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing flow alerts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowAlertService {

    private static final String REST_LOCKED_SOURCE = "rest-locked";
    private static final String REST_POLL_ATTEMPTS_METRIC = "flow_alerts.rest.poll.attempts";
    private static final String REST_POLL_ROWS_METRIC = "flow_alerts.rest.poll.rows";
    private static final String REST_POLL_DURATION_METRIC = "flow_alerts.rest.poll.duration";
    private static final String TRADE_START_TIME_FIELD = "tradeStartTime";
    
    private final FlowAlertRepository flowAlertRepository;
    private final UnusualWhalesClient unusualWhalesClient;
    private final UnusualWhalesWebSocketClient webSocketClient;
    private final UnusualWhalesConfig config;
    private final WebSocketConnectionManager connectionManager;
    private final FlowAlertBroadcaster flowAlertBroadcaster;
    private final FlowAlertMapper flowAlertMapper;
    private final BatchedFlowAlertPersistence batchedPersistence;
    private final FlowAlertBatchPersistence flowAlertBatchPersistence;
    private final FlowAlertAuditService auditService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    
    /**
     * Initialize WebSocket message handler on startup
     */
    @PostConstruct
    public void initialize() {
        if (config.getWebsocket().isEnabled()) {
            log.info("Registering WS flow alert handler");
            webSocketClient.onFlowAlert(this::handleWebSocketFlowAlert);
        }
    }
    
    /**
     * Handle flow alerts received from WebSocket
     * Broadcasts immediately for real-time updates, saves to DB asynchronously
     */
    public void handleWebSocketFlowAlert(UnusualWhalesFlowAlertDto dto) {
        try {
            // Track alert as received for audit purposes
            auditService.trackReceived(dto.id());
            
            // Broadcast immediately for real-time frontend updates
            flowAlertBroadcaster.broadcastFlowAlertImmediately(dto);
            
            // Queue for batched persistence (duplicate handling done at DB level)
            batchedPersistence.queueAlert(dto);
        } catch (Exception e) {
            log.error("Error handling WS flow alert {}: {}", dto.id(), e.getMessage(), e);
        }
    }
    
    /**
     * Sync flow alerts from the locked REST API poller.
     */
    @Async
    @Transactional
    public void syncFlowAlerts() {
        log.debug("[REST] sync - starting");
        long startedAtNanos = System.nanoTime();
        String outcome = "success";
        int fetchedCount = 0;
        int insertedCount = 0;
        int duplicateCount = 0;
        int invalidCount = 0;

        if (!hasUsableApiKey()) {
            log.warn("[REST] locked flow-alert poll disabled because UNUSUAL_WHALES_API_KEY is not configured");
            recordRestPollMetrics("disabled", 0, 0, 0, 0, System.nanoTime() - startedAtNanos);
            return;
        }

        try {
            UnusualWhalesFlowResponse response = unusualWhalesClient.getLockedFlowAlerts();
            
            if (response == null || response.data() == null) {
                outcome = "empty";
                log.warn("Received empty response from upstream market data API");
                return;
            }
            
            fetchedCount = response.data().size();
            List<FlowAlert> newAlerts = new ArrayList<>();
            
            for (UnusualWhalesFlowAlertDto dto : response.data()) {
                try {
                    newAlerts.add(flowAlertMapper.mapToEntity(dto, DataSourceEnum.REST));
                } catch (IllegalArgumentException exception) {
                    invalidCount++;
                    log.warn("[REST] skipping invalid flow alert {}: {}", dto.id(), exception.getMessage());
                }
            }
            
            if (!newAlerts.isEmpty()) {
                FlowAlertBatchPersistence.FlowAlertBatchResult result = flowAlertBatchPersistence.persistBatch(newAlerts);
                insertedCount = result.insertedAlerts().size();
                duplicateCount = result.duplicateCount();
                if (result.duplicateCount() > 0) {
                    log.debug("[REST] skipped {} duplicate flow alerts during conflict-safe insert", result.duplicateCount());
                }
                newAlerts = result.insertedAlerts();
            }
            
            log.debug("[REST] fetched={}, inserted={}, duplicates={}, invalid={}",
                fetchedCount, insertedCount, duplicateCount, invalidCount);
        } catch (Exception e) {
            outcome = "failure";
            log.error("Error during flow alerts sync", e);
            throw new IllegalStateException("Failed to sync flow alerts", e);
        } finally {
            recordRestPollMetrics(
                outcome,
                fetchedCount,
                insertedCount,
                duplicateCount,
                invalidCount,
                System.nanoTime() - startedAtNanos
            );
        }
    }

    private boolean hasUsableApiKey() {
        String apiKey = config.getApi().getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    private void recordRestPollMetrics(
        String outcome,
        int fetchedCount,
        int insertedCount,
        int duplicateCount,
        int invalidCount,
        long durationNanos
    ) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
            REST_POLL_ATTEMPTS_METRIC,
            "source", REST_LOCKED_SOURCE,
            "outcome", outcome
        ).increment();
        recordRestPollRows(meterRegistry, "fetched", fetchedCount);
        recordRestPollRows(meterRegistry, "inserted", insertedCount);
        recordRestPollRows(meterRegistry, "duplicate", duplicateCount);
        recordRestPollRows(meterRegistry, "invalid", invalidCount);
        Timer.builder(REST_POLL_DURATION_METRIC)
            .tag("source", REST_LOCKED_SOURCE)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private void recordRestPollRows(MeterRegistry meterRegistry, String result, int count) {
        meterRegistry.counter(
            REST_POLL_ROWS_METRIC,
            "source", REST_LOCKED_SOURCE,
            "result", result
        ).increment(count);
    }
    
    /**
     * Get flow alerts with pagination
     */
    @Transactional(readOnly = true)
    public Page<FlowAlert> getFlowAlerts(Pageable pageable) {
        return flowAlertRepository.findAllByOrderByTradeStartTimeDesc(pageable);
    }
    
    /**
     * Get flow alerts with filters
     */
    @Transactional(readOnly = true)
    public Page<FlowAlert> getFlowAlertsWithFilters(FlowAlertFilter filter, Pageable pageable) {
        var specification = FlowAlertSpecifications.buildSpecification(filter);
        return flowAlertRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FlowAlert> getFlowAlertsWithFilters(
        FlowAlertFilter filter,
        Instant newerThan,
        Instant olderThan,
        Pageable pageable
    ) {
        Specification<FlowAlert> specification = FlowAlertSpecifications.buildSpecification(filter);
        if (newerThan != null) {
            specification = specification.and((root, query, cb) ->
                cb.greaterThan(root.get(TRADE_START_TIME_FIELD), newerThan));
        }
        if (olderThan != null) {
            specification = specification.and((root, query, cb) ->
                cb.lessThan(root.get(TRADE_START_TIME_FIELD), olderThan));
        }
        return flowAlertRepository.findAll(specification, pageable);
    }
    
    /**
     * Get statistics about recent alerts
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        Instant last24Hours = Instant.now().minus(24, ChronoUnit.HOURS);
        long recentCount = flowAlertRepository.countRecentAlerts(last24Hours);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_alerts", flowAlertRepository.count());
        stats.put("last_24h_count", recentCount);
        stats.put("timestamp", Instant.now());
        
        return stats;
    }
}
