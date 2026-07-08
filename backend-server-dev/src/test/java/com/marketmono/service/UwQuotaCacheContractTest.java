package com.marketmono.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UwQuotaCacheContractTest {

    @Test
    void liveSectorPerformanceIsServedFromDatabaseSnapshots() {
        // The /sector-performance/live endpoint must not have a per-request
        // upstream fetch path anymore; it reads sector_performance_snapshots.
        assertThat(SectorPerformanceService.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().equals("getLiveSectorPerformance"));
        assertThat(SectorPerformanceSnapshotService.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("getSectorPerformanceFromDatabase"));
    }

    @Test
    void greekExposureByStrikeExpiryUsesGammaExposureCache() throws NoSuchMethodException {
        Cacheable cacheable = GreekExposureByStrikeExpiryService.class
            .getDeclaredMethod("getGreekExposureByStrikeExpiry", String.class, String.class, String.class)
            .getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly("gammaExposureCache");
        assertThat(cacheable.cacheManager()).isEqualTo("gammaExposureCacheManager");
    }

    @Test
    void unusualWhalesDailyLimitDefaultTracksObservedAccountLimit() throws Exception {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml).contains("daily-limit: ${UW_API_DAILY_LIMIT:300000}");
        assertThat(applicationYaml).doesNotContain("daily-limit: ${UW_API_DAILY_LIMIT:100000}");
    }
}
