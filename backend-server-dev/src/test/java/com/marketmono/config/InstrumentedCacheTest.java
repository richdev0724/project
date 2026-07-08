package com.marketmono.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentedCacheTest {

    private SimpleMeterRegistry meterRegistry;
    private InstrumentedCache cache;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cache = new InstrumentedCache(new ConcurrentMapCache("apiResponses"), meterRegistry);
    }

    @Test
    void shouldRecordCacheHitsAndMisses() {
        cache.put("spy", "payload");

        assertThat(cache.get("spy").get()).isEqualTo("payload");
        assertThat(cache.get("qqq")).isNull();

        assertThat(counter("marketmono.cache.requests", "result", "hit")).isEqualTo(1.0);
        assertThat(counter("marketmono.cache.requests", "result", "miss")).isEqualTo(1.0);
    }

    @Test
    void shouldRecordWritesAndEvictions() {
        cache.put("spy", "payload");
        cache.putIfAbsent("qqq", "other");
        cache.evict("spy");
        cache.clear();

        assertThat(counter("marketmono.cache.writes", "operation", "put")).isEqualTo(1.0);
        assertThat(counter("marketmono.cache.writes", "operation", "put_if_absent")).isEqualTo(1.0);
        assertThat(counter("marketmono.cache.evictions", "operation", "evict")).isEqualTo(1.0);
        assertThat(counter("marketmono.cache.evictions", "operation", "clear")).isEqualTo(1.0);
    }

    @Test
    void shouldRecordCallableLoadsAsMissesAndWrites() {
        assertThat(cache.get("spy", () -> "payload")).isEqualTo("payload");
        assertThat(cache.get("spy", () -> "other")).isEqualTo("payload");

        assertThat(counter("marketmono.cache.requests", "result", "miss")).isEqualTo(1.0);
        assertThat(counter("marketmono.cache.requests", "result", "hit")).isEqualTo(1.0);
        assertThat(counter("marketmono.cache.writes", "operation", "load")).isEqualTo(1.0);
    }

    private double counter(String name, String tagName, String tagValue) {
        return meterRegistry.find(name)
            .tag("cache", "apiResponses")
            .tag(tagName, tagValue)
            .counter()
            .count();
    }
}
