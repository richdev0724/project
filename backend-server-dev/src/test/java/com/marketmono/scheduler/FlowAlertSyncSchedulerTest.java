package com.marketmono.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAlertSyncSchedulerTest {

    @Test
    void shouldUseThirtySecondLockedRestPollSchedule() throws NoSuchMethodException {
        Method method = FlowAlertSyncScheduler.class.getMethod("syncFlowAlerts");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        SchedulerLock schedulerLock = method.getAnnotation(SchedulerLock.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${unusual-whales.flow.sync.interval-seconds:30}");
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.SECONDS);
        assertThat(schedulerLock.name()).isEqualTo("flowAlerts_lockedRestPoll");
        assertThat(schedulerLock.lockAtLeastFor()).isEqualTo("PT25S");
        assertThat(schedulerLock.lockAtMostFor()).isEqualTo("PT55S");
    }
}
