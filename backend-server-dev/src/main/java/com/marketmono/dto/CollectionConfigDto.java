package com.marketmono.dto;

import com.marketmono.entity.CollectionConfig;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for collection configuration values exposed to admin tooling.
 */
public record CollectionConfigDto(
    String configKey,
    List<String> tickers,
    String description,
    String updatedBy,
    LocalDateTime updatedAt
) {
    public static CollectionConfigDto from(CollectionConfig config, List<String> tickers) {
        return new CollectionConfigDto(
            config.getConfigKey(),
            tickers,
            config.getDescription(),
            config.getUpdatedBy(),
            config.getUpdatedAt()
        );
    }
}