package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeAggregatePageResponse;
import com.marketmono.util.DateUtils;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class HotOptionTradeAggregateCacheService {

    private static final int MAX_ENTRIES = 128;
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);
    private static final Duration DEFAULT_PRESET_REFRESH_INTERVAL = Duration.ofSeconds(5);
    private static final BigDecimal DEFAULT_FLOW_MIN_PREMIUM = BigDecimal.valueOf(50_000);
    private static final long DEFAULT_FLOW_MIN_VOLUME = 100L;
    private static final String DEFAULT_FLOW_FILTER_HASH = "default-flow-preset";
    private static final String DEFAULT_FLOW_SORT = "sip_timestamp";
    private static final String REDIS_KEY_PREFIX = "hot-option-trade-aggregates::";

    private final Cache<HotAggregatePageKey, OptionTradeAggregatePageResponse> cache =
        Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(CACHE_TTL)
            .build();

    private final AtomicLong lastDefaultPresetRefreshAtMs = new AtomicLong(0L);
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final boolean redisEnabled;

    public HotOptionTradeAggregateCacheService() {
        this((StringRedisTemplate) null, (ObjectMapper) null, null, false);
    }

    @Autowired
    public HotOptionTradeAggregateCacheService(
        ObjectProvider<StringRedisTemplate> redisTemplateProvider,
        ObjectProvider<ObjectMapper> objectMapperProvider,
        ObjectProvider<MeterRegistry> meterRegistryProvider,
        @Value("${active-active.redis.enabled:false}") boolean redisEnabled
    ) {
        this(
            redisTemplateProvider.getIfAvailable(),
            objectMapperProvider.getIfAvailable(),
            meterRegistryProvider.getIfAvailable(),
            redisEnabled
        );
    }

    HotOptionTradeAggregateCacheService(
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        boolean redisEnabled
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.redisEnabled = redisEnabled;
    }

    public OptionTradeAggregatePageResponse getIfPresent(HotAggregatePageKey key) {
        OptionTradeAggregatePageResponse local = cache.getIfPresent(key);
        if (local != null) {
            incrementRequestCounter("l1", "hit");
            return local;
        }

        incrementRequestCounter("l1", "miss");

        if (!isSecondLineRedisEligible(key) || stringRedisTemplate == null || objectMapper == null) {
            return null;
        }

        try {
            String serialized = stringRedisTemplate.opsForValue().get(redisKey(key));
            if (serialized == null || serialized.isBlank()) {
                incrementRequestCounter("l2", "miss");
                return null;
            }

            OptionTradeAggregatePageResponse response = objectMapper.readValue(
                serialized,
                OptionTradeAggregatePageResponse.class
            );
            cache.put(key, response);
            incrementRequestCounter("l2", "hit");
            return response;
        } catch (Exception exception) {
            incrementRequestCounter("l2", "error");
            log.warn("Failed to read second-line aggregate cache {}: {}", key, exception.getMessage());
            return null;
        }
    }

    public void put(HotAggregatePageKey key, OptionTradeAggregatePageResponse response) {
        if (key != null && response != null) {
            cache.put(key, response);
            incrementWriteCounter("l1");

            if (isSecondLineRedisEligible(key) && stringRedisTemplate != null && objectMapper != null) {
                try {
                    stringRedisTemplate.opsForValue().set(
                        redisKey(key),
                        objectMapper.writeValueAsString(response),
                        CACHE_TTL
                    );
                    incrementWriteCounter("l2");
                } catch (Exception exception) {
                    log.warn("Failed to write second-line aggregate cache {}: {}", key, exception.getMessage());
                }
            }
        }
    }

    public HotAggregatePageKey defaultFlowPresetKey() {
        return defaultFlowPresetKey(null);
    }

    public HotAggregatePageKey defaultFlowPresetKey(
        OptionTradeMarketDayResolver.ResolvedMarketDay resolvedDefaultDay
    ) {
        return new HotAggregatePageKey(
            1,
            150,
            DEFAULT_FLOW_MIN_PREMIUM,
            resolvedDefaultDay != null ? resolvedDefaultDay.day() : DateUtils.getCurrentDateInEasternTime(),
            DEFAULT_FLOW_FILTER_HASH,
            OptionTradeCountMode.SLICE,
            DEFAULT_FLOW_SORT
        );
    }

    public PageRequest defaultFlowPageable() {
        HotAggregatePageKey key = defaultFlowPresetKey();
        return PageRequest.of(
            0,
            key.pageSize(),
            Sort.by(
                Sort.Order.desc("lastExecutedAt"),
                Sort.Order.desc("aggregateId")
            )
        );
    }

    public OptionTradeAggregateService.OptionTradeAggregateFilter defaultFlowFilter() {
        return defaultFlowFilter(null);
    }

    public OptionTradeAggregateService.OptionTradeAggregateFilter defaultFlowFilter(
        OptionTradeMarketDayResolver.ResolvedMarketDay resolvedDefaultDay
    ) {
        return new OptionTradeAggregateService.OptionTradeAggregateFilter(
            null,                                      // tickers
            null,                                      // ignoreTickers
            resolvedDefaultDay != null ? resolvedDefaultDay.since() : DateUtils.startOfCurrentEasternDay(),
            resolvedDefaultDay != null ? resolvedDefaultDay.until() : null,
            null,                                      // expirations
            null,                                      // minExpiryDate
            null,                                      // maxExpiryDate
            defaultFlowPremium(),                      // minPremium
            null,                                      // maxPremium
            null,                                      // minSize
            null,                                      // maxSize
            DEFAULT_FLOW_MIN_VOLUME,                   // minVolume
            null,                                      // maxVolume
            null,                                      // calls
            null,                                      // puts
            null,                                      // sweepOnly
            null,                                      // floorOnly
            null,                                      // hasSweep
            null,                                      // hasFloor
            Boolean.TRUE,                              // hasSingleleg
            null,                                      // hasMultileg
            null,                                      // side
            null,                                      // cross
            null,                                      // regular
            null,                                      // bid
            null,                                      // ask
            null,                                      // noSide
            null,                                      // aboveAsk
            null,                                      // belowBid
            null,                                      // volumeGtOi
            null,                                      // sizeGtOi
            null,                                      // excludeDeepItm
            null,                                      // otmOnly
            null,                                      // itmOnly
            null                                       // hideExpired
        );
    }

    public boolean shouldRefreshDefaultFlowPreset() {
        long now = System.currentTimeMillis();
        long previous = lastDefaultPresetRefreshAtMs.get();
        if (now - previous < DEFAULT_PRESET_REFRESH_INTERVAL.toMillis()) {
            return false;
        }
        return lastDefaultPresetRefreshAtMs.compareAndSet(previous, now);
    }

    public void markDefaultFlowPresetRefreshFailed() {
        lastDefaultPresetRefreshAtMs.set(0L);
    }

    public boolean isDefaultFlowPreset(HotAggregatePageKey key) {
        return key != null
            && key.page() == 1
            && key.pageSize() == 150
            && key.minPremium() != null
            && key.minPremium().compareTo(DEFAULT_FLOW_MIN_PREMIUM) == 0
            && DEFAULT_FLOW_FILTER_HASH.equals(key.filterHash())
            && key.countMode() == OptionTradeCountMode.SLICE
            && DEFAULT_FLOW_SORT.equals(key.sort());
    }

    private boolean isSecondLineRedisEligible(HotAggregatePageKey key) {
        return redisEnabled && isDefaultFlowPreset(key);
    }

    private String redisKey(HotAggregatePageKey key) {
        return REDIS_KEY_PREFIX
            + key.page() + ':'
            + key.pageSize() + ':'
            + key.minPremium() + ':'
            + key.resolvedDay() + ':'
            + key.countMode() + ':'
            + key.sort() + ':'
            + key.filterHash();
    }

    private void incrementRequestCounter(String tier, String result) {
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
            "option_trade_aggregates.hot_cache.requests",
            "tier", tier,
            "result", result
        ).increment();
    }

    private void incrementWriteCounter(String tier) {
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(
            "option_trade_aggregates.hot_cache.writes",
            "tier", tier
        ).increment();
    }

    public static BigDecimal defaultFlowPremium() {
        return DEFAULT_FLOW_MIN_PREMIUM;
    }

    public static long defaultFlowVolume() {
        return DEFAULT_FLOW_MIN_VOLUME;
    }

    public static String defaultFlowFilterIdentity() {
        return DEFAULT_FLOW_FILTER_HASH;
    }

    public record HotAggregatePageKey(
        int page,
        int pageSize,
        BigDecimal minPremium,
        LocalDate resolvedDay,
        String filterHash,
        OptionTradeCountMode countMode,
        String sort
    ) {}
}
