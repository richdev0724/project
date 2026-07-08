package com.marketmono.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

final class InstrumentedCache implements Cache {

    private static final String REQUESTS_METRIC = "marketmono.cache.requests";
    private static final String WRITES_METRIC = "marketmono.cache.writes";
    private static final String EVICTIONS_METRIC = "marketmono.cache.evictions";

    private final Cache delegate;
    private final MeterRegistry meterRegistry;

    InstrumentedCache(Cache delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            ValueWrapper value = delegate.get(key);
            recordRequest(value == null ? "miss" : "hit");
            return value;
        } catch (RuntimeException exception) {
            recordRequest("error");
            throw exception;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            T value = delegate.get(key, type);
            recordRequest(value == null ? "miss" : "hit");
            return value;
        } catch (RuntimeException exception) {
            recordRequest("error");
            throw exception;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        AtomicBoolean loaded = new AtomicBoolean(false);
        try {
            T value = delegate.get(key, () -> {
                loaded.set(true);
                return valueLoader.call();
            });
            recordRequest(loaded.get() ? "miss" : "hit");
            if (loaded.get()) {
                recordWrite("load");
            }
            return value;
        } catch (RuntimeException exception) {
            recordRequest("error");
            throw exception;
        }
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
        recordWrite("put");
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existingValue = delegate.putIfAbsent(key, value);
        if (existingValue == null) {
            recordWrite("put_if_absent");
        }
        return existingValue;
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
        recordEviction("evict");
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean evicted = delegate.evictIfPresent(key);
        if (evicted) {
            recordEviction("evict");
        }
        return evicted;
    }

    @Override
    public void clear() {
        delegate.clear();
        recordEviction("clear");
    }

    @Override
    public boolean invalidate() {
        boolean invalidated = delegate.invalidate();
        if (invalidated) {
            recordEviction("clear");
        }
        return invalidated;
    }

    private void recordRequest(String result) {
        counter(REQUESTS_METRIC, "result", result).increment();
    }

    private void recordWrite(String operation) {
        counter(WRITES_METRIC, "operation", operation).increment();
    }

    private void recordEviction(String operation) {
        counter(EVICTIONS_METRIC, "operation", operation).increment();
    }

    private Counter counter(String name, String tagName, String tagValue) {
        return Counter.builder(name)
            .tag("cache", getName())
            .tag(tagName, tagValue)
            .register(meterRegistry);
    }
}
