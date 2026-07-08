package com.marketmono.service.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodic Kafka ingestion metrics reporter.
 * Logs throughput, persistence timing, and error rates every 30 seconds.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Lazy(false)
@RequiredArgsConstructor
@Slf4j
public class KafkaMetricsReporter {

    private final MeterRegistry meterRegistry;

    private final AtomicLong lastReceivedSnapshot = new AtomicLong(0);
    private final AtomicLong lastPersistedSnapshot = new AtomicLong(0);
    private final AtomicLong lastDeserErrorSnapshot = new AtomicLong(0);
    private final AtomicLong lastMapErrorSnapshot = new AtomicLong(0);
    private final AtomicLong lastDeadLetterSnapshot = new AtomicLong(0);
    private Instant startTime;
    private Instant lastReportTime;

    @PostConstruct
    public void init() {
        startTime = Instant.now();
        lastReportTime = startTime;
        log.info("[KAFKA-METRICS] Metrics reporter initialized — reporting every 30s");
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    @SchedulerLock(name = "kafka_reportMetrics", lockAtMostFor = "PT5M", lockAtLeastFor = "PT50S")
    public void reportMetrics() {
        Instant now = Instant.now();
        long intervalSec = Duration.between(lastReportTime, now).toSeconds();
        long uptimeSec = Duration.between(startTime, now).toSeconds();
        if (intervalSec <= 0) intervalSec = 1;

        // Read current counter values
        double totalReceived = getCounterValue("kafka.option_trades.received");
        double totalPersisted = getCounterValue("kafka.option_trades.persisted");
        double totalDeserErrors = getCounterValue("kafka.option_trades.deser_errors");
        double totalMapErrors = getCounterValue("kafka.option_trades.map_errors");
        double totalDeadLettered = getCounterValue("kafka.option_trades.dead_lettered");
        double totalRetries = getCounterValue("kafka.option_trades.persistence_retries");
        double totalFailed = getCounterValue("kafka.option_trades.failed");

        // Calculate interval deltas
        long received = (long) totalReceived - lastReceivedSnapshot.get();
        long persisted = (long) totalPersisted - lastPersistedSnapshot.get();
        // Update snapshots
        lastReceivedSnapshot.set((long) totalReceived);
        lastPersistedSnapshot.set((long) totalPersisted);
        lastDeserErrorSnapshot.set((long) totalDeserErrors);
        lastMapErrorSnapshot.set((long) totalMapErrors);
        lastDeadLetterSnapshot.set((long) totalDeadLettered);
        lastReportTime = now;

        // Throughput rates
        long receivedPerSec = received / intervalSec;
        long persistedPerSec = persisted / intervalSec;

        // Batch persistence timing from Micrometer Timer
        Timer batchTimer = meterRegistry.find("kafka.option_trades.batch_persist").timer();
        double avgBatchMs = 0;
        double maxBatchMs = 0;
        long batchCount = 0;
        if (batchTimer != null) {
            avgBatchMs = batchTimer.mean(TimeUnit.MILLISECONDS);
            maxBatchMs = batchTimer.max(TimeUnit.MILLISECONDS);
            batchCount = batchTimer.count();
        }

        // Estimated daily rate
        long estimatedDaily = (uptimeSec > 0) ? ((long) totalPersisted * 86400 / uptimeSec) : 0;

        String avgBatchFmt = String.format("%.1f", avgBatchMs);
        String maxBatchFmt = String.format("%.1f", maxBatchMs);
        String dailyMillions = String.format("%.1f", estimatedDaily / 1_000_000.0);

        log.info("[KAFKA-METRICS] interval={}s recv={} ({}/s) saved={} ({}/s) | total: recv={} saved={} | batch: n={} avg={}ms max={}ms | err: deser={} map={} retry={} dlt={} fail={} | est.daily={}M",
            intervalSec, received, receivedPerSec, persisted, persistedPerSec,
            (long) totalReceived, (long) totalPersisted,
            batchCount, avgBatchFmt, maxBatchFmt,
            (long) totalDeserErrors, (long) totalMapErrors, (long) totalRetries,
            (long) totalDeadLettered, (long) totalFailed, dailyMillions);
    }

    private double getCounterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter != null ? counter.count() : 0;
    }
}
