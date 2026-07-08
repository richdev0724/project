package com.marketmono.service;

import com.marketmono.dto.OptionTradeCountMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowFilterQueryCacheServiceTest {

    @Test
    void shouldCoalesceConcurrentRequestsForSameKey() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FlowFilterQueryCacheService service = new FlowFilterQueryCacheService(meterRegistry);
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key = cacheKey("raw-hot-default");
        AtomicInteger producerCalls = new AtomicInteger();
        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch releaseProducer = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            Future<String> producer = executor.submit(() -> service.getOrLoad(
                key,
                true,
                String.class,
                () -> {
                    producerCalls.incrementAndGet();
                    producerStarted.countDown();
                    await(releaseProducer);
                    return "cached-response";
                }
            ));

            assertThat(producerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            List<Future<String>> waiters = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                waiters.add(executor.submit(() -> service.getOrLoad(
                    key,
                    true,
                    String.class,
                    () -> {
                        producerCalls.incrementAndGet();
                        return "duplicate-response";
                    }
                )));
            }

            // Wait until every waiter has attached to the in-flight producer before releasing it.
            // Otherwise a slow waiter can miss both the cache and the in-flight entry and become
            // a second producer, making this test flaky on starved CI runners.
            awaitUntil(() -> requestCount(meterRegistry, key, "coalesced_waiter") == 5.0);

            releaseProducer.countDown();

            assertThat(producer.get(5, TimeUnit.SECONDS)).isEqualTo("cached-response");
            for (Future<String> waiter : waiters) {
                assertThat(waiter.get(5, TimeUnit.SECONDS)).isEqualTo("cached-response");
            }
            assertThat(producerCalls).hasValue(1);
        } finally {
            releaseProducer.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldServeCachedResponseWithoutCallingProducerAgain() {
        FlowFilterQueryCacheService service = new FlowFilterQueryCacheService();
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key = cacheKey("raw-hot-default");
        AtomicInteger producerCalls = new AtomicInteger();

        String first = service.getOrLoad(key, true, String.class, () -> {
            producerCalls.incrementAndGet();
            return "cached-response";
        });
        String second = service.getOrLoad(key, true, String.class, () -> {
            producerCalls.incrementAndGet();
            return "duplicate-response";
        });

        assertThat(first).isEqualTo("cached-response");
        assertThat(second).isEqualTo("cached-response");
        assertThat(producerCalls).hasValue(1);
    }

    @Test
    void shouldClearInflightEntryWhenProducerFails() {
        FlowFilterQueryCacheService service = new FlowFilterQueryCacheService();
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key = cacheKey("raw-hot-default");
        AtomicInteger producerCalls = new AtomicInteger();

        assertThatThrownBy(() -> service.getOrLoad(key, true, String.class, () -> {
            producerCalls.incrementAndGet();
            throw new IllegalStateException("producer failed");
        })).isInstanceOf(IllegalStateException.class)
            .hasMessage("producer failed");

        String result = service.getOrLoad(key, true, String.class, () -> {
            producerCalls.incrementAndGet();
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(producerCalls).hasValue(2);
    }

    @Test
    void shouldServeStaleSliceResponseWhenProducerTimesOutAfterFreshCacheExpires() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FlowFilterQueryCacheService service = new FlowFilterQueryCacheService(
            meterRegistry,
            Duration.ofMillis(50),
            Duration.ofMillis(1),
            Duration.ofMinutes(1),
            Duration.ofSeconds(5)
        );
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key = cacheKey("raw-hot-default");
        AtomicInteger producerCalls = new AtomicInteger();

        String first = service.getOrLoad(key, true, String.class, () -> {
            producerCalls.incrementAndGet();
            return "last-success";
        });

        long freshCacheExpiresAfter = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2);
        awaitUntil(() -> System.nanoTime() >= freshCacheExpiresAfter);

        String stale = service.getOrLoad(key, true, String.class, () -> {
            producerCalls.incrementAndGet();
            throw new QueryTimeoutException("producer timed out");
        });

        assertThat(first).isEqualTo("last-success");
        assertThat(stale).isEqualTo("last-success");
        assertThat(producerCalls).hasValue(2);
        Counter staleCounter = meterRegistry.find("flow.filters.query.cache.visible.responses")
            .tag("role", "stale")
            .tag("cause", "sql_timeout")
            .counter();
        assertThat(staleCounter).isNotNull();
        assertThat(staleCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldOpenNegativeCooldownAfterTimeoutWithoutStaleValue() {
        FlowFilterQueryCacheService service = new FlowFilterQueryCacheService(
            null,
            Duration.ofMillis(50),
            Duration.ofMillis(1),
            Duration.ofMinutes(1),
            Duration.ofSeconds(2)
        );
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key = cacheKey("raw-hot-default");
        AtomicInteger producerCalls = new AtomicInteger();

        assertThatThrownBy(() -> service.getOrLoad(key, true, String.class, () -> {
            producerCalls.incrementAndGet();
            throw new QueryTimeoutException("producer timed out");
        })).isInstanceOf(QueryTimeoutException.class);

        FlowFilterQueryCacheService.FlowFilterNegativeCooldownException cooldownException = assertThrows(
            FlowFilterQueryCacheService.FlowFilterNegativeCooldownException.class,
            () -> service.getOrLoad(key, true, String.class, () -> {
                producerCalls.incrementAndGet();
                return "should-not-run";
            })
        );

        assertThat(cooldownException.retryAfterSeconds()).isEqualTo(2);

        assertThat(producerCalls).hasValue(1);
    }

    @Test
    void shouldWrapWaiterTimeoutProducerFailureWithoutReusingProducerException() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FlowFilterQueryCacheService service = new FlowFilterQueryCacheService(meterRegistry);
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key = cacheKey("raw-hot-default");
        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch releaseProducer = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> producer = executor.submit(() -> service.getOrLoad(
                key,
                true,
                String.class,
                () -> {
                    producerStarted.countDown();
                    await(releaseProducer);
                    throw new QueryTimeoutException("producer timed out");
                }
            ));

            assertThat(producerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<String> waiter = executor.submit(() -> service.getOrLoad(
                key,
                true,
                String.class,
                () -> "duplicate-response"
            ));

            awaitUntil(() -> requestCount(meterRegistry, key, "coalesced_waiter") == 1.0);
            assertThat(waiter.isDone()).isFalse();
            releaseProducer.countDown();

            assertThatThrownBy(() -> producer.get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(QueryTimeoutException.class);
            assertThatThrownBy(() -> waiter.get(1, TimeUnit.SECONDS))
                .hasCauseInstanceOf(FlowFilterQueryCacheService.FlowFilterProducerFailureException.class);
        } finally {
            releaseProducer.countDown();
            executor.shutdownNow();
        }
    }

    private static double requestCount(
        SimpleMeterRegistry meterRegistry,
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key,
        String outcome
    ) {
        Counter counter = meterRegistry.find("flow.filters.query.cache.requests")
            .tag("endpoint", key.endpointTag())
            .tag("outcome", outcome)
            .tag("filter_shape", key.filterShape())
            .tag("count_mode", key.countMode().name().toLowerCase(Locale.ROOT))
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void shouldReturnSpecificTimeoutWhenWaiterOutlivesProducerBudget() throws Exception {
        FlowFilterQueryCacheService service = new FlowFilterQueryCacheService(null, Duration.ofMillis(25));
        FlowFilterQueryCacheService.FlowFilterQueryCacheKey key = cacheKey("raw-hot-default");
        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch releaseProducer = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> producer = executor.submit(() -> service.getOrLoad(
                key,
                true,
                String.class,
                () -> {
                    producerStarted.countDown();
                    await(releaseProducer);
                    return "cached-response";
                }
            ));

            assertThat(producerStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.getOrLoad(
                key,
                true,
                String.class,
                () -> "duplicate-response"
            )).isInstanceOf(FlowFilterQueryCacheService.FlowFilterQueryWaitTimeoutException.class)
                .hasMessage("Timed out waiting for Flow query producer");

            releaseProducer.countDown();
            assertThat(producer.get(1, TimeUnit.SECONDS)).isEqualTo("cached-response");
        } finally {
            releaseProducer.countDown();
            executor.shutdownNow();
        }
    }

    private static FlowFilterQueryCacheService.FlowFilterQueryCacheKey cacheKey(String filterHash) {
        return new FlowFilterQueryCacheService.FlowFilterQueryCacheKey(
            "/api/v1/option-trades",
            LocalDate.of(2026, 5, 15),
            "raw",
            filterHash,
            "default",
            OptionTradeCountMode.SLICE,
            1,
            100,
            "sip_timestamp"
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch", exception);
        }
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for condition");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for condition");
            }
        }
    }
}
