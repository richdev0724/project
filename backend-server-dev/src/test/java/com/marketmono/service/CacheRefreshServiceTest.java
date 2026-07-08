package com.marketmono.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CacheRefreshServiceTest {

    /**
     * The dashboard Mono Matrix is served database-only from
     * market_tide_snapshots (populated by MarketTideSnapshotScheduler), so the
     * legacy market-tide cache warmer must stay removed: nothing reads the
     * warmed "today" cache key anymore.
     */
    @Test
    void marketTideCacheWarmerIsRemoved() {
        boolean hasMarketTideWarmer = Arrays.stream(CacheRefreshService.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch(name -> name.equals("refreshMarketTideCache"));

        assertThat(hasMarketTideWarmer).isFalse();
    }

    @Test
    void marketTideSnapshotServiceServesDatabaseOnlyPath() throws NoSuchMethodException {
        Method method = MarketTideSnapshotService.class.getMethod("getMarketTideFromDatabase");
        assertThat(method).isNotNull();
    }
}
