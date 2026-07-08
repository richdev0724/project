package com.marketmono.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for cache management with different TTL settings per cache
 */
@Configuration
@ConditionalOnProperty(prefix = "active-active.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class CacheConfig {

    /**
     * Primary cache manager for general API responses (5-minute TTL)
     */
    @Bean
    @Primary
    public CacheManager cacheManager(MeterRegistry meterRegistry) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
        );
        // Set cache names that will use this default configuration
        cacheManager.setCacheNames(List.of("apiResponses", "marketPrices", "topNetImpact"));
        log.info("Configured primary cache manager with 5-minute TTL for: apiResponses, marketPrices, topNetImpact");
        return instrument(cacheManager, meterRegistry);
    }
    
    /**
     * Dedicated cache manager for sector performance with 1-hour TTL
     * Reduces upstream market data API calls from 3,300/day to 792/day (76% savings)
     */
    @Bean("sectorPerformanceCacheManager")
    public CacheManager sectorPerformanceCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating sectorPerformanceCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(20)  // Only 11 sector ETFs, small cache size
                .expireAfterWrite(Duration.ofHours(1))
        );
        cacheManager.setCacheNames(List.of("sectorPerformance"));
        log.info("Configured sector performance cache manager with 1-hour TTL (33 API calls/hour vs 33/page load)");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for market-tide data with 1-minute TTL
     */
    @Bean("marketTideCacheManager")
    public CacheManager marketTideCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating marketTideCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(1))
        );
        cacheManager.setCacheNames(List.of("marketTideCache"));
        log.info("Configured market-tide cache manager with 1-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for economic calendar data with 30-minute TTL
     * Allows dynamic cache creation for any cache name
     */
    @Bean(name = "economicCalendarCacheManager")
    public CacheManager economicCalendarCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating economicCalendarCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(30))
        );
        // Don't set cache names - allow dynamic creation
        log.info("Configured economic calendar cache manager with 30-minute TTL for dynamic caches");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for gamma exposure data with 5-minute TTL
     */
    @Bean("gammaExposureCacheManager")
    public CacheManager gammaExposureCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating gammaExposureCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(200)  // Cache gamma data for multiple tickers and dates
                .expireAfterWrite(Duration.ofMinutes(5))
        );
        cacheManager.setCacheNames(List.of("gammaExposureCache"));
        log.info("Configured gamma exposure cache manager with 5-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for earnings data with 1-minute TTL
     */
    @Bean("earningsCacheManager")
    public CacheManager earningsCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating earningsCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(300)  // Cache earnings data for multiple tickers and dates
                .expireAfterWrite(Duration.ofMinutes(1))
        );
        cacheManager.setCacheNames(List.of("earningsCache"));
        log.info("Configured earnings cache manager with 1-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for OHLC candle data with 1-minute TTL
     * OHLC data updates frequently during trading hours, so short TTL is appropriate
     */
    @Bean("ohlcCacheManager")
    public CacheManager ohlcCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating ohlcCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(500)  // Cache OHLC data for multiple tickers, candle sizes, and timeframes
                .expireAfterWrite(Duration.ofMinutes(1))
        );
        cacheManager.setCacheNames(List.of("ohlcCache"));
        log.info("Configured OHLC cache manager with 1-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for spot exposure data with 5-minute TTL
     */
    @Bean("spotExposureCacheManager")
    public CacheManager spotExposureCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating spotExposureCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(200)  // Cache spot exposure data for multiple tickers and dates
                .expireAfterWrite(Duration.ofMinutes(5))
        );
        cacheManager.setCacheNames(List.of("spotExposureCache"));
        log.info("Configured spot exposure cache manager with 5-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    @Bean("spotGexLatestSnapshotCacheManager")
    public CacheManager spotGexLatestSnapshotCacheManager(MeterRegistry meterRegistry) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(60))
        );
        cacheManager.setCacheNames(List.of("spotGexLatestSnapshotCache"));
        log.info("Configured Spot GEX latest snapshot cache manager with 60-second TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for volume price levels data with 5-minute TTL
     */
    @Bean("volumePriceLevelsCacheManager")
    public CacheManager volumePriceLevelsCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating volumePriceLevelsCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(200)  // Cache volume price levels for multiple tickers and dates
                .expireAfterWrite(Duration.ofMinutes(5))
        );
        cacheManager.setCacheNames(List.of("volumePriceLevelsCache"));
        log.info("Configured volume price levels cache manager with 5-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for IV rank data with 5-minute TTL
     * IV rank data updates periodically, 5-minute TTL balances freshness with API usage
     */
    @Bean("ivRankCacheManager")
    public CacheManager ivRankCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating ivRankCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(200)  // Cache IV rank data for multiple tickers and timespans
                .expireAfterWrite(Duration.ofMinutes(5))
        );
        cacheManager.setCacheNames(List.of("ivRankCache"));
        log.info("Configured IV rank cache manager with 5-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for realized volatility data with 5-minute TTL
     * Realized volatility data updates periodically, 5-minute TTL balances freshness with API usage
     */
    @Bean("realizedVolatilityCacheManager")
    public CacheManager realizedVolatilityCacheManager(MeterRegistry meterRegistry) {
        log.info("🔍 DEBUG: Creating realizedVolatilityCacheManager bean");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(200)  // Cache realized volatility data for multiple tickers and timeframes
                .expireAfterWrite(Duration.ofMinutes(5))
        );
        cacheManager.setCacheNames(List.of("realizedVolatilityCache"));
        log.info("Configured realized volatility cache manager with 5-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for market status checks used by the spot GEX scheduler.
     */
    @Bean("marketStatusCacheManager")
    public CacheManager marketStatusCacheManager(MeterRegistry meterRegistry) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(Duration.ofMinutes(1))
        );
        cacheManager.setCacheNames(List.of("marketStatus"));
        log.info("Configured market status cache manager with 1-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    /**
     * Dedicated cache manager for collection_config reads used by scheduled collection.
     */
    @Bean("collectionConfigCacheManager")
    public CacheManager collectionConfigCacheManager(MeterRegistry meterRegistry) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterWrite(Duration.ofMinutes(5))
        );
        cacheManager.setCacheNames(List.of("collectionConfig"));
        log.info("Configured collection config cache manager with 5-minute TTL");
        return instrument(cacheManager, meterRegistry);
    }

    private CacheManager instrument(CacheManager cacheManager, MeterRegistry meterRegistry) {
        return new InstrumentedCacheManager(cacheManager, meterRegistry);
    }
}
