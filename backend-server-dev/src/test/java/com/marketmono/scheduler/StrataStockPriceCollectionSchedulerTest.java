package com.marketmono.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StrataStockPriceCollectionSchedulerTest {

    @Test
    void shouldScheduleCollectionAtOneMinuteFixedRate() throws NoSuchMethodException {
        Method method = StrataStockPriceCollectionScheduler.class.getMethod("collectLatestPrices");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedRateString())
            .isEqualTo("${market-data.strata-stock-prices.interval-ms:60000}");
        assertThat(scheduled.fixedDelayString()).isEmpty();
    }
}
