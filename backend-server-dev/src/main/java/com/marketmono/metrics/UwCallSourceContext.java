package com.marketmono.metrics;

import java.util.function.Supplier;

public final class UwCallSourceContext {

    private static final ThreadLocal<UwCallSource> CURRENT = ThreadLocal.withInitial(() -> UwCallSource.UNKNOWN);

    private UwCallSourceContext() {}

    public static UwCallSource current() {
        UwCallSource source = CURRENT.get();
        return source != null ? source : UwCallSource.UNKNOWN;
    }

    public static void runWith(UwCallSource source, Runnable runnable) {
        try (Scope ignored = open(source)) {
            runnable.run();
        }
    }

    public static <T> T callWith(UwCallSource source, Supplier<T> supplier) {
        try (Scope ignored = open(source)) {
            return supplier.get();
        }
    }

    public static Scope open(UwCallSource source) {
        UwCallSource previous = current();
        CURRENT.set(source != null ? source : UwCallSource.UNKNOWN);
        return () -> CURRENT.set(previous);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
