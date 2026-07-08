package com.marketmono.dto;

import com.marketmono.entity.ChangelogEntry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Admin-facing flat changelog item DTO.
 */
public record AdminChangelogEntryDto(
    UUID id,
    String releaseVersion,
    LocalDate releaseDate,
    String entryType,
    String entryText,
    int sortOrder,
    String visibility,
    String createdBy,
    String updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static AdminChangelogEntryDto fromEntity(ChangelogEntry entry) {
        return new AdminChangelogEntryDto(
            entry.getId(),
            entry.getReleaseVersion(),
            entry.getReleaseDate(),
            entry.getEntryType().name().toLowerCase(Locale.ROOT),
            entry.getEntryText(),
            entry.getSortOrder(),
            entry.getVisibility().name().toLowerCase(Locale.ROOT),
            entry.getCreatedBy(),
            entry.getUpdatedBy(),
            entry.getCreatedAt(),
            entry.getUpdatedAt()
        );
    }
}