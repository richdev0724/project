package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.exception.OhlcRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OhlcServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private OhlcService ohlcService;

    @Test
    void shouldCoalesceConcurrentIdenticalOhlcMisses() throws Exception {
        UnusualWhalesOhlcDto response = new UnusualWhalesOhlcDto(List.of(
            new UnusualWhalesOhlcDto.OhlcItem(
                "2026-05-21T13:30:00Z",
                "2026-05-21T13:31:00Z",
                null,
                "740.00",
                "741.00",
                "739.00",
                "740.50",
                1_000L,
                1_000L,
                "r"
            )
        ));
        CountDownLatch clientEntered = new CountDownLatch(1);
        CountDownLatch releaseClient = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger clientCalls = new AtomicInteger();
        AtomicReference<Thread> secondThread = new AtomicReference<>();

        when(unusualWhalesClient.getOhlcData(
            eq("SPY"),
            eq("1m"),
            eq(null),
            eq("1D"),
            eq(null),
            eq(null)
        )).thenAnswer(invocation -> {
            clientCalls.incrementAndGet();
            clientEntered.countDown();
            assertThat(releaseClient.await(3, TimeUnit.SECONDS)).isTrue();
            return response;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UnusualWhalesOhlcDto> first = executor.submit(() ->
                ohlcService.getOhlcData("SPY", "1m", null, "1D", null, null)
            );
            assertThat(clientEntered.await(3, TimeUnit.SECONDS)).isTrue();

            Future<UnusualWhalesOhlcDto> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                return ohlcService.getOhlcData("SPY", "1m", null, "1D", null, null);
            });
            assertThat(secondStarted.await(3, TimeUnit.SECONDS)).isTrue();
            awaitCondition(
                () -> clientCalls.get() == 1 && isWaiting(secondThread.get()),
                "second OHLC request should be waiting on the coalesced producer"
            );

            releaseClient.countDown();

            assertThat(first.get(3, TimeUnit.SECONDS)).isSameAs(response);
            assertThat(second.get(3, TimeUnit.SECONDS)).isSameAs(response);
            assertThat(clientCalls.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        verify(unusualWhalesClient, times(1)).getOhlcData(
            "SPY",
            "1m",
            null,
            "1D",
            null,
            null
        );
    }

    private static void awaitCondition(BooleanSupplier condition, String description) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }

        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private static boolean isWaiting(Thread thread) {
        if (thread == null) {
            return false;
        }

        Thread.State state = thread.getState();
        return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
    }

    @Test
    void shouldRetryAfterProducerFailure() {
        UnusualWhalesOhlcDto response = new UnusualWhalesOhlcDto(List.of(
            new UnusualWhalesOhlcDto.OhlcItem(
                "2026-05-21T13:30:00Z",
                "2026-05-21T13:31:00Z",
                null,
                "740.00",
                "741.00",
                "739.00",
                "740.50",
                1_000L,
                1_000L,
                "r"
            )
        ));

        when(unusualWhalesClient.getOhlcData(
            eq("SPY"),
            eq("1m"),
            eq(null),
            eq("1D"),
            eq(null),
            eq(null)
        ))
            .thenThrow(new IllegalStateException("upstream unavailable"))
            .thenReturn(response);

        assertThatThrownBy(() ->
            ohlcService.getOhlcData("SPY", "1m", null, "1D", null, null)
        )
            .isInstanceOf(OhlcRequestException.class)
            .hasMessageContaining("Failed to fetch OHLC data");

        assertThat(ohlcService.getOhlcData("SPY", "1m", null, "1D", null, null))
            .isSameAs(response);

        verify(unusualWhalesClient, times(2)).getOhlcData(
            "SPY",
            "1m",
            null,
            "1D",
            null,
            null
        );
    }
}
