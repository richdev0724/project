package com.marketmono.service;

import com.marketmono.config.OptionTradesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class FlowFilterLoadGuard {

    private final OptionTradesProperties optionTradesProperties;
    private final ConcurrentMap<String, Semaphore> expensiveQuerySemaphores = new ConcurrentHashMap<>();

    public GuardPermit tryAcquire(String endpoint, String filterShape) {
        OptionTradesProperties.Guard guard = optionTradesProperties.getGuard();
        if (guard == null || !guard.isEnabled() || !isExpensiveFilterShape(filterShape)) {
            return GuardPermit.noop();
        }

        int maxConcurrentQueries = Math.max(1, guard.getMaxConcurrentExpensiveQueries());
        String key = safeKey(endpoint) + ":" + safeKey(filterShape);
        Semaphore semaphore = expensiveQuerySemaphores.compute(
            key,
            (ignored, existing) -> existing == null || existing.availablePermits() > maxConcurrentQueries
                ? new Semaphore(maxConcurrentQueries)
                : existing
        );

        if (!semaphore.tryAcquire()) {
            return GuardPermit.busy(Math.max(1, guard.getRetryAfterSeconds()));
        }

        return GuardPermit.acquired(semaphore);
    }

    private boolean isExpensiveFilterShape(String filterShape) {
        return filterShape != null && filterShape.contains("expensive");
    }

    private String safeKey(String value) {
        return Objects.toString(value, "unknown");
    }

    public static final class GuardPermit implements AutoCloseable {
        private static final GuardPermit NOOP = new GuardPermit(null, false, 0);

        private final Semaphore semaphore;
        private final boolean busy;
        private final int retryAfterSeconds;
        private boolean released;

        private GuardPermit(Semaphore semaphore, boolean busy, int retryAfterSeconds) {
            this.semaphore = semaphore;
            this.busy = busy;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public static GuardPermit noop() {
            return NOOP;
        }

        public static GuardPermit busy(int retryAfterSeconds) {
            return new GuardPermit(null, true, retryAfterSeconds);
        }

        private static GuardPermit acquired(Semaphore semaphore) {
            return new GuardPermit(semaphore, false, 0);
        }

        public boolean isBusy() {
            return busy;
        }

        public int retryAfterSeconds() {
            return retryAfterSeconds;
        }

        @Override
        public void close() {
            if (semaphore != null && !released) {
                released = true;
                semaphore.release();
            }
        }
    }
}
