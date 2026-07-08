package com.marketmono.service;

import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.FlowAlertId;
import com.marketmono.repository.FlowAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Dual-criteria batched persistence service for WebSocket flow alerts.
 * Batches alerts based on:
 * 1. Maximum batch size (count)
 * 2. Maximum batch bytes (size)
 * 3. Maximum flush interval (time)
 *
 * Whichever criterion is met first triggers a batch flush.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchedFlowAlertPersistence {
    
    private final FlowAlertRepository flowAlertRepository;
    private final FlowAlertMapper flowAlertMapper;
    @Lazy
    private final FlowAlertAuditService auditService;
    private final FlowAlertBatchPersistence flowAlertBatchPersistence;
    private final UnusualWhalesConfig config;
    private final MeterRegistry meterRegistry;
    
    private final BlockingQueue<UnusualWhalesFlowAlertDto> buffer = new LinkedBlockingQueue<>(1000);
    private final Set<String> inFlightIds = ConcurrentHashMap.newKeySet();
    
    private Counter wsReceivedCounter;
    private Counter wsQueuedCounter;
    private Counter wsPersistedCounter;
    private Counter wsDuplicatesCounter;
    private Counter wsDroppedCounter;
    
    @PostConstruct
    public void initMetrics() {
        wsReceivedCounter = meterRegistry.counter("flow_alerts.ws.received");
        wsQueuedCounter = meterRegistry.counter("flow_alerts.ws.queued");
        wsPersistedCounter = meterRegistry.counter("flow_alerts.ws.persisted");
        wsDuplicatesCounter = meterRegistry.counter("flow_alerts.ws.duplicates");
        wsDroppedCounter = meterRegistry.counter("flow_alerts.ws.dropped");
        
        Gauge.builder("flow_alerts.ws.queue_size", buffer, BlockingQueue::size)
            .description("Current number of alerts in the persistence queue")
            .register(meterRegistry);
        
        log.info("✅ Micrometer metrics initialized for batched flow alert persistence");
    }
    
    /**
     * Queue an alert for batched persistence.
     * Non-blocking - returns immediately.
     *
     * @param dto the alert to queue
     */
    public void queueAlert(UnusualWhalesFlowAlertDto dto) {
        wsReceivedCounter.increment();
        
        boolean queued = buffer.offer(dto);
        if (queued) {
            wsQueuedCounter.increment();
        } else {
            wsDroppedCounter.increment();
            log.error("⚠️ Queue FULL! Dropping alert: {} (queue size: {})", dto.id(), buffer.size());
        }
    }
    
    /**
     * Flush batches based on dual criteria:
     * - Maximum batch size (count)
     * - Maximum batch bytes (size)
     * - Maximum flush interval (time-based via @Scheduled)
     *
     * Runs every configured flush interval.
     */
    @Scheduled(fixedDelayString = "#{@unusualWhalesConfig.websocket.batch.flushIntervalMs}")
    @SchedulerLock(name = "flowAlerts_flushBatch", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void flushBatch() {
        if (buffer.isEmpty()) {
            return;
        }
        
        List<UnusualWhalesFlowAlertDto> batch = extractBatchWithDualCriteria();
        
        if (batch.isEmpty()) {
            return;
        }
        
        // Mark IDs as in-flight for audit visibility
        batch.forEach(dto -> inFlightIds.add(dto.id()));

        try {
            List<FlowAlert> entities = batch.stream()
                .map(dto -> flowAlertMapper.mapToEntity(dto, DataSourceEnum.WEBSOCKET))
                .toList();

            List<FlowAlert> newEntities = filterNewUniqueAlerts(entities);

            if (newEntities.isEmpty()) {
                log.debug("[BATCH] Skipped {} duplicate alerts", entities.size());
                return;
            }
            
            FlowAlertBatchPersistence.FlowAlertBatchResult result = flowAlertBatchPersistence.persistBatch(newEntities);
            wsPersistedCounter.increment(result.insertedAlerts().size());
            if (result.duplicateCount() > 0) {
                wsDuplicatesCounter.increment(result.duplicateCount());
                log.debug("[BATCH] Skipped {} duplicate alerts during conflict-safe insert", result.duplicateCount());
            }

            result.insertedAlerts().forEach(entity -> auditService.trackPersisted(entity.getAlertId().toString()));
            
            log.debug("[BATCH] Saved {} alerts", result.insertedAlerts().size());
        } catch (Exception e) {
            log.error("❌ Failed to persist batch of {} alerts: {}", batch.size(), e.getMessage(), e);
            // Re-queue alerts for retry to avoid data loss
            int requeued = 0;
            for (UnusualWhalesFlowAlertDto dto : batch) {
                if (buffer.offer(dto)) {
                    requeued++;
                } else {
                    wsDroppedCounter.increment();
                    log.error("⚠️ Re-queue FAILED due to full buffer for alert {}", dto.id());
                }
            }
            if (requeued > 0) {
                log.warn("Re-queued {} alerts after batch failure for retry on next flush", requeued);
            }
        } finally {
            // Clear in-flight markers
            batch.forEach(dto -> inFlightIds.remove(dto.id()));
        }
    }
    
    /**
     * Extract a batch of alerts from the buffer using dual criteria:
     * 1. Stop if batch reaches maxSize
     * 2. Stop if adding next alert would exceed maxBytes
     *
     * @return list of alerts to persist
     */
    private List<UnusualWhalesFlowAlertDto> extractBatchWithDualCriteria() {
        List<UnusualWhalesFlowAlertDto> batch = new ArrayList<>();
        long totalBytes = 0;
        
        int maxSize = config.getWebsocket().getBatch().getMaxSize();
        long maxBytes = config.getWebsocket().getBatch().getMaxBytes();
        
        while (!buffer.isEmpty() && batch.size() < maxSize) {
            UnusualWhalesFlowAlertDto dto = buffer.peek();
            if (dto == null) {
                break;
            }
            
            long estimatedSize = estimateSize(dto);
            
            // Check if adding this alert would exceed byte limit
            if (totalBytes + estimatedSize > maxBytes && !batch.isEmpty()) {
                log.debug("Batch byte limit reached: {} bytes (max: {}), stopping extraction",
                    totalBytes, maxBytes);
                break;
            }
            
            // Remove from buffer and add to batch
            buffer.poll();
            batch.add(dto);
            totalBytes += estimatedSize;
        }
        
        if (log.isDebugEnabled() && !batch.isEmpty()) {
            log.debug("Extracted batch: {} alerts, ~{} bytes", batch.size(), totalBytes);
        }
        
        return batch;
    }
    
    /**
     * Estimate the memory size of a DTO in bytes.
     * Conservative estimation based on field sizes.
     *
     * @param dto the alert DTO
     * @return estimated size in bytes
     */
    private long estimateSize(UnusualWhalesFlowAlertDto dto) {
        // Conservative estimation:
        // - Each string field: length * 2 bytes (UTF-16) + 40 bytes overhead
        // - Each numeric field: 8 bytes
        // - Each boolean: 1 byte
        // - Object overhead: ~40 bytes
        
        long size = 40; // Base object overhead
        
        if (dto.id() != null) size += dto.id().length() * 2 + 40;
        if (dto.ticker() != null) size += dto.ticker().length() * 2 + 40;
        if (dto.optionChain() != null) size += dto.optionChain().length() * 2 + 40;
        if (dto.alertRule() != null) size += dto.alertRule().length() * 2 + 40;
        
        size += 8 * 15; // ~15 numeric fields (decimals, integers, longs)
        size += 5; // ~5 boolean fields
        
        return size;
    }

    /**
     * Remove duplicate flow-alert keys before persistence.
     *
    * flow_alerts is market-data history, but the live app can receive duplicate alerts.
    * Duplicate WebSocket messages should be counted and skipped instead of failing the
    * scheduled flusher and polluting runtime logs.
     */
    private List<FlowAlert> filterNewUniqueAlerts(List<FlowAlert> entities) {
        Map<FlowAlertId, FlowAlert> uniqueById = new LinkedHashMap<>();

        for (FlowAlert entity : entities) {
            if (uniqueById.putIfAbsent(entity.getId(), entity) != null) {
                wsDuplicatesCounter.increment();
            }
        }

        Set<FlowAlertId> existingIds = flowAlertRepository.findAllById(uniqueById.keySet()).stream()
            .map(FlowAlert::getId)
            .collect(Collectors.toSet());

        return uniqueById.entrySet().stream()
            .filter(entry -> {
                boolean exists = existingIds.contains(entry.getKey());
                if (exists) {
                    wsDuplicatesCounter.increment();
                }
                return !exists;
            })
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Get current queue depth for monitoring.
     *
     * @return number of alerts in the buffer
     */
    public int getQueueDepth() {
        return buffer.size();
    }

    /**
     * Check whether a specific alert ID is currently queued (waiting to be flushed).
     */
    boolean isInQueue(String alertId) {
        for (UnusualWhalesFlowAlertDto dto : buffer) {
            if (alertId.equals(dto.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a specific alert ID is currently being persisted (in-flight).
     */
    boolean isInFlight(String alertId) {
        return inFlightIds.contains(alertId);
    }
}
