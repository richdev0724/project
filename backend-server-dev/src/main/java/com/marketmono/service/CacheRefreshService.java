package com.marketmono.service;

import com.marketmono.metrics.UwCallSource;
import com.marketmono.metrics.UwCallSourceContext;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CacheRefreshService {

    private static final String SECTOR_PERFORMANCE_CACHE_NAME = "sectorPerformance";
    private static final String SECTOR_PERFORMANCE_KEY = "all";

    private final SectorPerformanceService sectorPerformanceService;
    private final CacheManager sectorPerformanceCacheManager;

    public CacheRefreshService(
        SectorPerformanceService sectorPerformanceService,
        @Qualifier("sectorPerformanceCacheManager") CacheManager sectorPerformanceCacheManager
    ) {
        this.sectorPerformanceService = sectorPerformanceService;
        this.sectorPerformanceCacheManager = sectorPerformanceCacheManager;
    }

    /**
     * Proactive refresh SectorPerformance every 55min (1hr TTL - 5min buffer)
     */
    @Scheduled(fixedRate = 3300000) // 55min (1hr TTL buffer)
    @SchedulerLock(name = "cacheRefresh_sectorPerformance", lockAtMostFor = "PT60M", lockAtLeastFor = "PT50M")
    public void refreshSectorPerformanceCache() {
        UwCallSourceContext.runWith(UwCallSource.CACHE_WARMER_SECTOR, () -> {
            try {
                var data = sectorPerformanceService.getSectorPerformanceUncached();
                putIfPresent(sectorPerformanceCacheManager, SECTOR_PERFORMANCE_CACHE_NAME, SECTOR_PERFORMANCE_KEY, data);
                int sectorCount = data != null ? data.size() : 0;
                log.info("[PROACTIVE] SectorPerformance cache refreshed for key {} ({} sectors)", SECTOR_PERFORMANCE_KEY, sectorCount);
            } catch (Exception e) {
                log.warn("SectorPerformance cache refresh failed", e);
            }
        });
    }

    private void putIfPresent(CacheManager cacheManager, String cacheName, String key, Object value) {
        if (value == null) {
            log.warn("Skipping proactive refresh for cache {} key {} because fetched value is null", cacheName, key);
            return;
        }

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("Cache {} is not available for proactive refresh", cacheName);
            return;
        }

        cache.put(key, value);
    }
}
