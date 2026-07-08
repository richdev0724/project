package com.marketmono.scheduler;

import com.marketmono.service.FlowAlertBatchPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowAlertLedgerCleanupSchedulerTest {

    @Mock
    private FlowAlertBatchPersistence persistence;

    @Test
    void shouldCreateSpringBeanWithTestOnlyClockConstructorPresent() {
        new ApplicationContextRunner()
            .withBean(FlowAlertBatchPersistence.class, () -> persistence)
            .withBean(FlowAlertLedgerCleanupScheduler.class)
            .withPropertyValues(
                "unusual-whales.flow.sync.retention-days=30",
                "unusual-whales.flow.sync.ledger-cleanup-grace-days=2"
            )
            .run(context ->
                assertThat(context).hasSingleBean(FlowAlertLedgerCleanupScheduler.class)
            );
    }

    @Test
    void shouldCleanupLedgerAfterRetentionWindowPlusGrace() {
        Instant now = Instant.parse("2026-06-25T12:00:00Z");
        Instant expectedCutoff = Instant.parse("2026-05-24T12:00:00Z");
        FlowAlertLedgerCleanupScheduler scheduler = new FlowAlertLedgerCleanupScheduler(
            persistence,
            30,
            2,
            Clock.fixed(now, ZoneOffset.UTC)
        );
        when(persistence.cleanupIngestKeysBefore(expectedCutoff)).thenReturn(7);

        scheduler.cleanupIngestKeys();

        verify(persistence).cleanupIngestKeysBefore(expectedCutoff);
    }
}
