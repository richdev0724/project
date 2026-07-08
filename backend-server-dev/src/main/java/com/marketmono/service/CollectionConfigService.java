package com.marketmono.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.CollectionConfigAuditLogDto;
import com.marketmono.dto.CollectionConfigDto;
import com.marketmono.entity.CollectionConfig;
import com.marketmono.entity.CollectionConfigAuditLog;
import com.marketmono.repository.CollectionConfigAuditLogRepository;
import com.marketmono.repository.CollectionConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for collection_config read and write operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionConfigService {

    private static final int MAX_TICKER_COUNT = 50;

    private final CollectionConfigRepository repository;
    private final CollectionConfigAuditLogRepository auditLogRepository;
    private final SpotGexCollectionProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "collectionConfig", key = "'spot_gex_tickers'", cacheManager = "collectionConfigCacheManager")
    public List<String> getSpotGexTickers() {
        return repository.findByConfigKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)
            .map(this::extractTickers)
            .filter(tickers -> !tickers.isEmpty())
            .orElseGet(this::getFallbackTickers);
    }

    @Transactional(readOnly = true)
    public CollectionConfigDto getSpotGexTickerConfig() {
        return repository.findByConfigKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)
            .map(config -> CollectionConfigDto.from(config, extractTickers(config)))
            .orElseGet(() -> new CollectionConfigDto(
                CollectionConfig.SPOT_GEX_TICKERS_KEY,
                getFallbackTickers(),
                CollectionConfig.SPOT_GEX_TICKERS_DESCRIPTION,
                "system-fallback",
                null
            ));
    }

    @Transactional
    @CacheEvict(value = "collectionConfig", key = "'spot_gex_tickers'", cacheManager = "collectionConfigCacheManager")
    public CollectionConfigDto updateSpotGexTickers(List<String> tickers, UUID adminId, String ipAddress) {
        List<String> normalizedTickers = normalizeTickers(tickers);
        if (normalizedTickers.isEmpty()) {
            throw new IllegalArgumentException("Ticker list cannot be empty after normalization");
        }

        Optional<CollectionConfig> existing = repository.findByConfigKey(CollectionConfig.SPOT_GEX_TICKERS_KEY);
        String oldValue = existing.map(CollectionConfig::getConfigValue).orElse(null);
        String newValue = writeTickerJson(normalizedTickers);

        repository.upsertConfig(
            CollectionConfig.SPOT_GEX_TICKERS_KEY,
            newValue,
            CollectionConfig.SPOT_GEX_TICKERS_DESCRIPTION,
            adminId != null ? adminId.toString() : "system"
        );

        auditLogRepository.save(CollectionConfigAuditLog.createEntry(
            adminId,
            CollectionConfig.SPOT_GEX_TICKERS_KEY,
            oldValue,
            newValue,
            ipAddress
        ));

        log.info("[SPOT-GEX] Updated ticker config: {} tickers, adminId={}, ip={}",
            normalizedTickers.size(), adminId, ipAddress);

        CollectionConfig saved = repository.findByConfigKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)
            .orElseThrow(() -> new IllegalStateException("Spot GEX ticker config was not persisted"));
        return CollectionConfigDto.from(saved, normalizedTickers);
    }

    @Transactional(readOnly = true)
    public List<CollectionConfigAuditLogDto> getSpotGexTickerAuditLog(boolean all) {
        List<CollectionConfigAuditLog> logs = all
            ? auditLogRepository.findAllByConfigKeyOrderByChangedAtDesc(CollectionConfig.SPOT_GEX_TICKERS_KEY)
            : auditLogRepository.findTop20ByConfigKeyOrderByChangedAtDesc(CollectionConfig.SPOT_GEX_TICKERS_KEY);

        return logs.stream()
            .map(log -> CollectionConfigAuditLogDto.from(
                log,
                parseTickerJson(log.getOldValue()),
                parseTickerJson(log.getNewValue())
            ))
            .toList();
    }

    private List<String> extractTickers(CollectionConfig config) {
        List<String> tickers = parseTickerJson(config.getConfigValue());
        if (tickers.isEmpty()) {
            log.warn("[SPOT-GEX] collection_config row '{}' was empty/invalid, using YAML fallback",
                CollectionConfig.SPOT_GEX_TICKERS_KEY);
            return getFallbackTickers();
        }
        return tickers;
    }

    private List<String> getFallbackTickers() {
        List<String> fallbackTickers = normalizeTickers(properties.getFallbackTickers());
        log.info("[SPOT-GEX] No DB ticker config found, using YAML fallback with {} tickers", fallbackTickers.size());
        return fallbackTickers;
    }

    private List<String> parseTickerJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }

        try {
            List<String> tickers = objectMapper.readValue(rawJson, new TypeReference<List<String>>() {});
            return normalizeTickers(tickers);
        } catch (JsonProcessingException ex) {
            log.warn("[SPOT-GEX] Failed to parse ticker JSON config '{}': {}",
                CollectionConfig.SPOT_GEX_TICKERS_KEY, ex.getMessage());
            return List.of();
        }
    }

    private String writeTickerJson(List<String> tickers) {
        try {
            return objectMapper.writeValueAsString(tickers);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize ticker config to JSON", ex);
        }
    }

    private List<String> normalizeTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String ticker : tickers) {
            if (!StringUtils.hasText(ticker)) {
                continue;
            }

            String normalizedTicker = ticker.trim().toUpperCase(Locale.US);
            validateTicker(normalizedTicker);
            normalized.add(normalizedTicker);
        }

        if (normalized.size() > MAX_TICKER_COUNT) {
            throw new IllegalArgumentException("Ticker list cannot exceed " + MAX_TICKER_COUNT + " entries");
        }

        return new ArrayList<>(normalized);
    }

    private void validateTicker(String ticker) {
        if (ticker.length() > 10) {
            throw new IllegalArgumentException("Ticker cannot exceed 10 characters: " + ticker);
        }
        if (!ticker.matches("^[A-Z][A-Z0-9.\\-]{0,9}$")) {
            throw new IllegalArgumentException("Invalid ticker format: " + ticker);
        }
    }
}