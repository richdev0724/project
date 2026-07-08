package com.marketmono.dto;

import com.marketmono.entity.CollectionConfigAuditLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for collection configuration audit history.
 */
public record CollectionConfigAuditLogDto(
    UUID id,
    UUID adminId,
    String configKey,
    List<String> oldTickers,
    List<String> newTickers,
    LocalDateTime changedAt,
    String ipAddress
) {
    public static CollectionConfigAuditLogDto from(
            CollectionConfigAuditLog log,
            List<String> oldTickers,
            List<String> newTickers) {
        return new CollectionConfigAuditLogDto(
            log.getId(),
            log.getAdminId(),
            log.getConfigKey(),
            oldTickers,
            newTickers,
            log.getChangedAt(),
            log.getIpAddress()
        );
    }
}