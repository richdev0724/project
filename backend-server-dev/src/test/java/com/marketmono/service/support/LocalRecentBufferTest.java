package com.marketmono.service.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LocalRecentBufferTest {

    @Test
    void snapshotPreservesAppendOrderAndTrimsOldestEntries() {
        LocalRecentBuffer<Integer> buffer = new LocalRecentBuffer<>(3);

        buffer.appendAll(List.of(1, 2));
        buffer.appendAll(List.of(3, 4));

        assertThat(buffer.getBufferSize()).isEqualTo(3);
        assertThat(buffer.snapshot(10)).containsExactly(2, 3, 4);
        assertThat(buffer.snapshot(2)).containsExactly(3, 4);
    }

    @Test
    void snapshotIsDefensiveAndUsesCappedLimit() {
        LocalRecentBuffer<Integer> buffer = new LocalRecentBuffer<>(3);
        buffer.appendAll(List.of(1, 2, 3));

        List<Integer> snapshot = buffer.snapshot(100);
        assertThatCode(() -> snapshot.add(4)).isInstanceOf(UnsupportedOperationException.class);

        buffer.appendAll(List.of(4));

        assertThat(snapshot).containsExactly(1, 2, 3);
        assertThat(buffer.snapshot(0)).containsExactly(4);
    }

    @Test
    void filteredNewestFirstSnapshotScansPastRejectedRecentItemsUntilLimitIsSatisfied() {
        LocalRecentBuffer<Integer> buffer = new LocalRecentBuffer<>(6);
        buffer.appendAll(List.of(1, 2, 3, 4, 5, 6));

        List<Integer> snapshot = buffer.snapshotNewestFirst(2, value -> value % 2 == 0 && value < 6);

        assertThat(snapshot).containsExactly(4, 2);
    }

    @Test
    void concurrentAppendsKeepCapacityAndPerWriterOrder() throws Exception {
        int writers = 4;
        int itemsPerWriter = 100;
        LocalRecentBuffer<Integer> buffer = new LocalRecentBuffer<>(writers * itemsPerWriter);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        AtomicBoolean capacityExceeded = new AtomicBoolean(false);
        var executor = Executors.newFixedThreadPool(writers);

        for (int writer = 0; writer < writers; writer++) {
            int base = writer * 1000;
            executor.submit(() -> {
                try {
                    start.await();
                    for (int index = 0; index < itemsPerWriter; index++) {
                        buffer.appendAll(List.of(base + index));
                        if (buffer.getBufferSize() > writers * itemsPerWriter) {
                            capacityExceeded.set(true);
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        List<Integer> snapshot = buffer.snapshot(writers * itemsPerWriter);

        assertThat(capacityExceeded).isFalse();
        assertThat(snapshot).hasSize(writers * itemsPerWriter);
        for (int writer = 0; writer < writers; writer++) {
            int base = writer * 1000;
            List<Integer> writerItems = snapshot.stream()
                .filter(value -> value >= base && value < base + itemsPerWriter)
                .toList();
            assertThat(writerItems).isSortedAccordingTo(Comparator.naturalOrder());
        }
    }

    @Test
    void concurrentSnapshotsAreDefensiveWhileAppendsContinue() throws Exception {
        LocalRecentBuffer<Integer> buffer = new LocalRecentBuffer<>(50);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch snapshotDone = new CountDownLatch(1);
        AtomicBoolean snapshotMutated = new AtomicBoolean(false);
        List<List<Integer>> snapshots = new ArrayList<>();
        var executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                start.await();
                for (int index = 0; index < 100; index++) {
                    buffer.appendAll(List.of(index));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        executor.submit(() -> {
            try {
                start.await();
                do {
                    List<Integer> snapshot = buffer.snapshot(50);
                    snapshots.add(snapshot);
                    int sizeBefore = snapshot.size();
                    try {
                        snapshot.add(-1);
                        snapshotMutated.set(true);
                    } catch (UnsupportedOperationException expected) {
                        assertThat(snapshot).hasSize(sizeBefore);
                    }
                    assertThat(snapshot).hasSizeLessThanOrEqualTo(50);
                } while (done.getCount() > 0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                snapshotDone.countDown();
            }
        });

        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(snapshotDone.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        assertThat(snapshotMutated).isFalse();
        assertThat(snapshots).isNotEmpty();
        assertThat(buffer.getBufferSize()).isEqualTo(50);
        assertThat(buffer.snapshot(50)).containsExactlyElementsOf(
            java.util.stream.IntStream.range(50, 100).boxed().toList()
        );
    }
}
