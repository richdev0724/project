package com.marketmono.service;

import com.marketmono.config.UnusualWhalesConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory audit service for tracking WebSocket flow alert persistence.
 * Tracks received vs persisted alert IDs to detect data loss.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowAlertAuditService {
    private final ConcurrentHashMap<String, Instant> receivedAlerts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> persistedAlerts = new ConcurrentHashMap<>();
    private final UnusualWhalesConfig config;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger missingCurrentGauge = new AtomicInteger(0);
    private final ObjectProvider<BatchedFlowAlertPersistence> batchedPersistenceProvider;

    @jakarta.annotation.PostConstruct
    void initMetrics() {
        meterRegistry.gauge("flow_alerts.ws.audit.missing_current", missingCurrentGauge);
    }
    
    /**
     * Track an alert as received from WebSocket.
     *
     * @param alertId the unique alert ID
     */
    public void trackReceived(String alertId) {
        receivedAlerts.put(alertId, Instant.now());
    }
    
    /**
     * Track an alert as successfully persisted to database.
     *
     * @param alertId the unique alert ID
     */
    public void trackPersisted(String alertId) {
        persistedAlerts.put(alertId, Instant.now());
    }
    
    /**
     * Audit report comparing received vs persisted alerts.
     * Runs every 5 minutes to detect missing persisted alerts.
     */
    @Scheduled(fixedRate = 300000)  // Every 5 minutes
    @SchedulerLock(name = "flowAlerts_auditReport", lockAtMostFor = "PT10M", lockAtLeastFor = "PT4M")
    public void auditReport() {
        // Compute grace window: allow time for batching and DB write before flagging as missing
        long flushIntervalMs = config.getWebsocket().getBatch().getFlushIntervalMs();
        long graceMs = Math.max(10_000L, flushIntervalMs * 3L); // at least 10s or 3x flush interval
        Instant now = Instant.now();

        // Start with all received IDs not yet marked persisted
        Set<String> missing = new HashSet<>(receivedAlerts.keySet());
        missing.removeAll(persistedAlerts.keySet());

        // Filter out alerts that are too new, still queued, or in-flight
        BatchedFlowAlertPersistence batchedPersistence = batchedPersistenceProvider.getIfAvailable();
        if (batchedPersistence != null) {
            missing.removeIf(id -> {
                Instant ts = receivedAlerts.get(id);
                boolean tooNew = ts == null || ts.isAfter(now.minusMillis(graceMs));
                boolean queued = batchedPersistence.isInQueue(id);
                boolean inFlight = batchedPersistence.isInFlight(id);
                return tooNew || queued || inFlight;
            });
        } else {
            log.warn("Audit service missing batchedPersistence reference; skipping in-flight/queue filters");
            missing.removeIf(id -> {
                Instant ts = receivedAlerts.get(id);
                return ts == null || ts.isAfter(now.minusMillis(graceMs));
            });
        }
        
        int receivedCount = receivedAlerts.size();
        int persistedCount = persistedAlerts.size();
        int missingCount = missing.size();
        missingCurrentGauge.set(missingCount);
        
        if (!missing.isEmpty()) {
            log.error("⚠️ ALERT LOSS DETECTED: {} alerts received but not persisted (total received: {}, persisted: {})",
                missingCount, receivedCount, persistedCount);
            
            if (log.isDebugEnabled() && missingCount <= 10) {
                log.debug("Missing alert IDs: {}", missing);
            } else if (missingCount > 10) {
                log.error("Too many missing alerts to log ({} total), check queue capacity and batch processing", missingCount);
            }
        } else {
            log.debug("✅ Audit OK: All {} received alerts persisted", receivedCount);
        }
        
        // Cleanup old entries (older than 10 minutes)
        cleanupOldEntries();
    }
    
    /**
     * Remove entries older than 10 minutes to prevent memory leaks.
     */
    private void cleanupOldEntries() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        int receivedBefore = receivedAlerts.size();
        int persistedBefore = persistedAlerts.size();
        
        receivedAlerts.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        persistedAlerts.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        
        int receivedRemoved = receivedBefore - receivedAlerts.size();
        int persistedRemoved = persistedBefore - persistedAlerts.size();
        
        if (receivedRemoved > 0 || persistedRemoved > 0) {
            log.debug("Cleaned up {} received and {} persisted audit entries older than 10 minutes",
                receivedRemoved, persistedRemoved);
        }
    }
    
    /**
     * Get current audit statistics.
     *
     * @return map with received, persisted, and missing counts
     */
    public java.util.Map<String, Integer> getAuditStats() {
        Set<String> missing = new HashSet<>(receivedAlerts.keySet());
        missing.removeAll(persistedAlerts.keySet());
        
        return java.util.Map.of(
            "received", receivedAlerts.size(),
            "persisted", persistedAlerts.size(),
            "missing", missing.size()
        );
    }
}
