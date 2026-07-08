package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.CollectionConfigDto;
import com.marketmono.entity.CollectionConfig;
import com.marketmono.repository.CollectionConfigAuditLogRepository;
import com.marketmono.repository.CollectionConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionConfigServiceTest {

    @Mock
    private CollectionConfigRepository repository;

    @Mock
    private CollectionConfigAuditLogRepository auditLogRepository;

    private CollectionConfigService collectionConfigService;

    @BeforeEach
    void setUp() {
        SpotGexCollectionProperties properties = new SpotGexCollectionProperties();
        properties.setFallbackTickers(List.of("SPY", "QQQ"));
        collectionConfigService = new CollectionConfigService(
            repository,
            auditLogRepository,
            properties,
            new ObjectMapper()
        );
    }

    @Test
    void shouldReturnNormalizedDbTickerConfigWhenPresent() {
        CollectionConfig config = CollectionConfig.builder()
            .configKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)
            .configValue("[\"spy\",\"QQQ\",\"SPY\"]")
            .description(CollectionConfig.SPOT_GEX_TICKERS_DESCRIPTION)
            .updatedBy("admin")
            .updatedAt(LocalDateTime.of(2026, 4, 6, 12, 0))
            .build();

        when(repository.findByConfigKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)).thenReturn(Optional.of(config));

        List<String> tickers = collectionConfigService.getSpotGexTickers();

        assertThat(tickers).containsExactly("SPY", "QQQ");
    }

    @Test
    void shouldFallBackToYamlTickersWhenDbConfigMissing() {
        when(repository.findByConfigKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)).thenReturn(Optional.empty());

        List<String> tickers = collectionConfigService.getSpotGexTickers();

        assertThat(tickers).containsExactly("SPY", "QQQ");
    }

    @Test
    void shouldPersistUpdatedTickersAndCreateAuditLog() {
        CollectionConfig existing = CollectionConfig.builder()
            .configKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)
            .configValue("[\"SPY\"]")
            .description(CollectionConfig.SPOT_GEX_TICKERS_DESCRIPTION)
            .updatedBy("system")
            .updatedAt(LocalDateTime.of(2026, 4, 6, 11, 0))
            .build();
        CollectionConfig updated = CollectionConfig.builder()
            .configKey(CollectionConfig.SPOT_GEX_TICKERS_KEY)
            .configValue("[\"SPY\",\"QQQ\"]")
            .description(CollectionConfig.SPOT_GEX_TICKERS_DESCRIPTION)
            .updatedBy(UUID.randomUUID().toString())
            .updatedAt(LocalDateTime.of(2026, 4, 6, 12, 0))
            .build();

        when(repository.findByConfigKey(CollectionConfig.SPOT_GEX_TICKERS_KEY))
            .thenReturn(Optional.of(existing), Optional.of(updated));

        CollectionConfigDto response = collectionConfigService.updateSpotGexTickers(
            List.of("spy", "QQQ", "SPY"),
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "127.0.0.1"
        );

        assertThat(response.tickers()).containsExactly("SPY", "QQQ");
        verify(repository).upsertConfig(
            eq(CollectionConfig.SPOT_GEX_TICKERS_KEY),
            eq("[\"SPY\",\"QQQ\"]"),
            eq(CollectionConfig.SPOT_GEX_TICKERS_DESCRIPTION),
            eq("11111111-1111-1111-1111-111111111111")
        );
        verify(auditLogRepository).save(any());
    }
}