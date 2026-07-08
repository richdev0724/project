package com.marketmono.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class InstrumentedCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final MeterRegistry meterRegistry;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    InstrumentedCacheManager(CacheManager delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            caches.remove(name);
            return null;
        }
        return caches.computeIfAbsent(name, ignored -> new InstrumentedCache(cache, meterRegistry));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
