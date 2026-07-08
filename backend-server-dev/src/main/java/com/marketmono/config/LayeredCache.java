package com.marketmono.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.Map;
import java.util.concurrent.Callable;

@Slf4j
public class LayeredCache implements Cache {

    private final String name;
    private final Cache levelOneCache;
    private final Cache levelTwoCache;

    public LayeredCache(String name, Cache levelOneCache, Cache levelTwoCache) {
        this.name = name;
        this.levelOneCache = levelOneCache;
        this.levelTwoCache = levelTwoCache;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return Map.of(
            "levelOne", levelOneCache.getNativeCache(),
            "levelTwo", levelTwoCache.getNativeCache()
        );
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper levelOneValue = levelOneCache.get(key);
        if (levelOneValue != null) {
            return levelOneValue;
        }

        ValueWrapper levelTwoValue = safeGetFromLevelTwo(key);
        if (levelTwoValue != null) {
            levelOneCache.put(key, levelTwoValue.get());
        }
        return levelTwoValue;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T levelOneValue = levelOneCache.get(key, type);
        if (levelOneValue != null) {
            return levelOneValue;
        }

        T levelTwoValue = safeGetFromLevelTwo(key, type);
        if (levelTwoValue != null) {
            levelOneCache.put(key, levelTwoValue);
        }
        return levelTwoValue;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper existingValue = get(key);
        if (existingValue != null) {
            @SuppressWarnings("unchecked")
            T cachedValue = (T) existingValue.get();
            return cachedValue;
        }

        try {
            T loadedValue = valueLoader.call();
            put(key, loadedValue);
            return loadedValue;
        } catch (Exception exception) {
            throw new ValueRetrievalException(key, valueLoader, exception);
        }
    }

    @Override
    public void put(Object key, Object value) {
        levelOneCache.put(key, value);
        safePutToLevelTwo(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existingValue = get(key);
        if (existingValue != null) {
            return existingValue;
        }

        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        levelOneCache.evict(key);
        safeEvictFromLevelTwo(key);
    }

    @Override
    public void clear() {
        levelOneCache.clear();
        safeClearLevelTwo();
    }

    private ValueWrapper safeGetFromLevelTwo(Object key) {
        try {
            return levelTwoCache.get(key);
        } catch (RuntimeException exception) {
            log.warn("Redis L2 cache get failed for {}[{}]: {}", name, key, exception.getMessage());
            return null;
        }
    }

    private <T> T safeGetFromLevelTwo(Object key, Class<T> type) {
        try {
            return levelTwoCache.get(key, type);
        } catch (RuntimeException exception) {
            log.warn("Redis L2 cache typed get failed for {}[{}]: {}", name, key, exception.getMessage());
            return null;
        }
    }

    private void safePutToLevelTwo(Object key, Object value) {
        try {
            levelTwoCache.put(key, value);
        } catch (RuntimeException exception) {
            log.warn("Redis L2 cache put failed for {}[{}]: {}", name, key, exception.getMessage());
        }
    }

    private void safeEvictFromLevelTwo(Object key) {
        try {
            levelTwoCache.evict(key);
        } catch (RuntimeException exception) {
            log.warn("Redis L2 cache evict failed for {}[{}]: {}", name, key, exception.getMessage());
        }
    }

    private void safeClearLevelTwo() {
        try {
            levelTwoCache.clear();
        } catch (RuntimeException exception) {
            log.warn("Redis L2 cache clear failed for {}: {}", name, exception.getMessage());
        }
    }
}