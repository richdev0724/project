package com.marketmono.scheduler;

import com.marketmono.service.FlowAlertBatchPersistence;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(prefix = "unusual-whales.flow.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FlowAlertLedgerCleanupScheduler {

    private final FlowAlertBatchPersistence persistence;
    private final int retentionDays;
    private final int graceDays;
    private final Clock clock;

    @Autowired
    public FlowAlertLedgerCleanupScheduler(
        FlowAlertBatchPersistence persistence,
        @Value("${unusual-whales.flow.sync.retention-days:30}") int retentionDays,
        @Value("${unusual-whales.flow.sync.ledger-cleanup-grace-days:2}") int graceDays
    ) {
        this(persistence, retentionDays, graceDays, Clock.systemUTC());
    }

    FlowAlertLedgerCleanupScheduler(
        FlowAlertBatchPersistence persistence,
        int retentionDays,
        int graceDays,
        Clock clock
    ) {
        this.persistence = persistence;
        this.retentionDays = retentionDays;
        this.graceDays = graceDays;
        this.clock = clock;
    }

    @Scheduled(cron = "${unusual-whales.flow.sync.ledger-cleanup-schedule:0 20 2 * * ?}")
    @SchedulerLock(name = "flowAlerts_cleanupIngestKeys", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void cleanupIngestKeys() {
        Instant cutoff = Instant.now(clock).minus((long) retentionDays + graceDays, ChronoUnit.DAYS);
        int deleted = persistence.cleanupIngestKeysBefore(cutoff);
        log.info(
            "[REST] cleaned {} flow-alert dedupe ledger rows older than {} (retentionDays={}, graceDays={})",
            deleted,
            cutoff,
            retentionDays,
            graceDays
        );
    }
}
