package com.marketmono.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;

class LayeredCacheTest {

    private ConcurrentMapCache levelOneCache;
    private ConcurrentMapCache levelTwoCache;
    private LayeredCache layeredCache;

    @BeforeEach
    void setUp() {
        levelOneCache = new ConcurrentMapCache("test-l1");
        levelTwoCache = new ConcurrentMapCache("test-l2");
        layeredCache = new LayeredCache("test", levelOneCache, levelTwoCache);
    }

    @Test
    void shouldPopulateLevelOneFromLevelTwoHit() {
        levelTwoCache.put("spy", "payload");

        assertThat(layeredCache.get("spy").get()).isEqualTo("payload");
        assertThat(levelOneCache.get("spy").get()).isEqualTo("payload");
    }

    @Test
    void shouldWriteThroughToBothLevels() {
        layeredCache.put("qqq", "value");

        assertThat(levelOneCache.get("qqq").get()).isEqualTo("value");
        assertThat(levelTwoCache.get("qqq").get()).isEqualTo("value");
    }

    @Test
    void shouldEvictFromBothLevels() {
        layeredCache.put("iwm", "value");

        layeredCache.evict("iwm");

        assertThat(levelOneCache.get("iwm")).isNull();
        assertThat(levelTwoCache.get("iwm")).isNull();
    }
}