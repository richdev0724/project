package com.marketmono.scheduler;

import com.marketmono.service.BatchedFlowAlertPersistence;
import com.marketmono.service.CacheRefreshService;
import com.marketmono.service.FlowAlertAuditService;
import com.marketmono.service.OffLitTradeIngestionService;
import com.marketmono.service.SectorEtfCollectionService;
import com.marketmono.service.auth.EmailValidationService;
import com.marketmono.service.kafka.KafkaListenerStartup;
import com.marketmono.service.kafka.KafkaMetricsReporter;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledLockCoverageTest {

    private static final List<Class<?>> LOCKED_SCHEDULED_TYPES = List.of(
        CacheRefreshService.class,
        BatchedFlowAlertPersistence.class,
        SectorEtfCollectionService.class,
        OffLitTradeIngestionService.class,
        FlowAlertAuditService.class,
        EmailValidationService.class,
        StockPriceCollectionScheduler.class,
        SpotGexCollectionScheduler.class,
        PoliticianSyncScheduler.class,
        FlowAlertSyncScheduler.class,
        KafkaMetricsReporter.class
    );

    @Test
    void shouldApplySchedulerLockToAllBusinessScheduledMethods() {
        for (Class<?> type : LOCKED_SCHEDULED_TYPES) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Scheduled.class)) {
                    assertThat(method.getAnnotation(SchedulerLock.class))
                        .withFailMessage("Expected @SchedulerLock on %s#%s", type.getSimpleName(), method.getName())
                        .isNotNull();
                }
            }
        }
    }

    @Test
    void shouldLeaveKafkaListenerRetryUnlocked() throws NoSuchMethodException {
        Method retryMethod = KafkaListenerStartup.class.getDeclaredMethod("retryStartListeners");

        assertThat(retryMethod.getAnnotation(Scheduled.class)).isNotNull();
        assertThat(retryMethod.getAnnotation(SchedulerLock.class)).isNull();
    }
}