package com.marketmono.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
@ConditionalOnProperty(prefix = "active-active.redis", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ActiveActiveRedisCacheConfig implements CachingConfigurer {

    private static final String REDIS_CACHE_NAMESPACE = "aa-v2::";

    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectMapper objectMapper;

    @Bean
    @Primary
    public CacheManager cacheManager(MeterRegistry meterRegistry) {
        log.info("Configuring Redis-only primary cache manager for apiResponses, marketPrices, and topNetImpact");
        return instrument(createRedisCacheManager(Map.of(
            "apiResponses", Duration.ofMinutes(5),
            "marketPrices", Duration.ofMinutes(5),
            "topNetImpact", Duration.ofMinutes(5)
        )), meterRegistry);
    }

    @Bean("sectorPerformanceCacheManager")
    public CacheManager sectorPerformanceCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("sectorPerformance", Duration.ofHours(1), meterRegistry);
    }

    @Bean("marketTideCacheManager")
    public CacheManager marketTideCacheManager(MeterRegistry meterRegistry) {
        return singleLayeredCacheManager("marketTideCache", 500, Duration.ofSeconds(30), Duration.ofMinutes(1), meterRegistry);
    }

    @Bean(name = "economicCalendarCacheManager")
    public CacheManager economicCalendarCacheManager(MeterRegistry meterRegistry) {
        return instrument(createRedisCacheManager(Map.of("economicCalendar", Duration.ofMinutes(30))), meterRegistry);
    }

    @Bean("gammaExposureCacheManager")
    public CacheManager gammaExposureCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("gammaExposureCache", Duration.ofMinutes(5), meterRegistry);
    }

    @Bean("earningsCacheManager")
    public CacheManager earningsCacheManager(MeterRegistry meterRegistry) {
        return singleLayeredCacheManager("earningsCache", 300, Duration.ofSeconds(30), Duration.ofMinutes(1), meterRegistry);
    }

    @Bean("ohlcCacheManager")
    public CacheManager ohlcCacheManager(MeterRegistry meterRegistry) {
        return singleLayeredCacheManager("ohlcCache", 500, Duration.ofSeconds(30), Duration.ofMinutes(1), meterRegistry);
    }

    @Bean("spotExposureCacheManager")
    public CacheManager spotExposureCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("spotExposureCache", Duration.ofMinutes(5), meterRegistry);
    }

    @Bean("spotGexLatestSnapshotCacheManager")
    public CacheManager spotGexLatestSnapshotCacheManager(MeterRegistry meterRegistry) {
        return singleLayeredCacheManager(
            "spotGexLatestSnapshotCache",
            1000,
            Duration.ofSeconds(15),
            Duration.ofSeconds(60),
            meterRegistry
        );
    }

    @Bean("volumePriceLevelsCacheManager")
    public CacheManager volumePriceLevelsCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("volumePriceLevelsCache", Duration.ofMinutes(5), meterRegistry);
    }

    @Bean("ivRankCacheManager")
    public CacheManager ivRankCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("ivRankCache", Duration.ofMinutes(5), meterRegistry);
    }

    @Bean("realizedVolatilityCacheManager")
    public CacheManager realizedVolatilityCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("realizedVolatilityCache", Duration.ofMinutes(5), meterRegistry);
    }

    @Bean("marketStatusCacheManager")
    public CacheManager marketStatusCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("marketStatus", Duration.ofMinutes(1), meterRegistry);
    }

    @Bean("collectionConfigCacheManager")
    public CacheManager collectionConfigCacheManager(MeterRegistry meterRegistry) {
        return singleRedisCacheManager("collectionConfig", Duration.ofMinutes(5), meterRegistry);
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache get failed for {}[{}]: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache put failed for {}[{}]: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache evict failed for {}[{}]: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache clear failed for {}: {}", cache.getName(), exception.getMessage());
            }
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return cacheErrorHandler();
    }

    private CacheManager singleRedisCacheManager(String cacheName, Duration ttl, MeterRegistry meterRegistry) {
        return instrument(createRedisCacheManager(Map.of(cacheName, ttl)), meterRegistry);
    }

    private CacheManager createRedisCacheManager(Map<String, Duration> cacheTtls) {
        RedisCacheConfiguration defaultConfiguration = baseRedisConfiguration(Duration.ofMinutes(5));
        Map<String, RedisCacheConfiguration> cacheConfigurations = cacheTtls.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> baseRedisConfiguration(entry.getValue())
            ));

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfiguration)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }

    private CacheManager singleLayeredCacheManager(
        String cacheName,
        long maximumSize,
        Duration levelOneTtl,
        Duration levelTwoTtl,
        MeterRegistry meterRegistry
    ) {
        CaffeineCacheManager levelOneManager = new CaffeineCacheManager(cacheName);
        levelOneManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(levelOneTtl)
        );

        CacheManager levelTwoManager = createRedisCacheManager(Map.of(cacheName, levelTwoTtl));

        Cache levelOneCache = Objects.requireNonNull(levelOneManager.getCache(cacheName), () -> "Missing L1 cache: " + cacheName);
        Cache levelTwoCache = Objects.requireNonNull(levelTwoManager.getCache(cacheName), () -> "Missing L2 cache: " + cacheName);

        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(new LayeredCache(cacheName, levelOneCache, levelTwoCache)));
        cacheManager.initializeCaches();

        log.info("Configured layered cache {} with Caffeine L1={} and Redis L2={}", cacheName, levelOneTtl, levelTwoTtl);
        return instrument(cacheManager, meterRegistry);
    }

    private CacheManager instrument(CacheManager cacheManager, MeterRegistry meterRegistry) {
        return new InstrumentedCacheManager(cacheManager, meterRegistry);
    }

    static GenericJackson2JsonRedisSerializer createRedisValueSerializer(ObjectMapper baseObjectMapper) {
        ObjectMapper redisObjectMapper = baseObjectMapper.copy();
        redisObjectMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.marketmono.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .build(),
            DefaultTyping.EVERYTHING,
            JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(redisObjectMapper);
    }

    private RedisCacheConfiguration baseRedisConfiguration(Duration ttl) {
        GenericJackson2JsonRedisSerializer redisValueSerializer = createRedisValueSerializer(objectMapper);

        return RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .computePrefixWith(cacheName -> REDIS_CACHE_NAMESPACE + cacheName + "::")
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                redisValueSerializer
            ))
            .entryTtl(ttl);
    }
}
