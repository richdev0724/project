package com.marketmono.dto;

import com.marketmono.entity.ChangelogEntry;

import java.util.Locale;

/**
 * Public changelog item exposed on the Help page.
 */
public record PublicChangelogChangeDto(
    String type,
    String text
) {

    public static PublicChangelogChangeDto fromEntity(ChangelogEntry entry) {
        return new PublicChangelogChangeDto(
            entry.getEntryType().name().toLowerCase(Locale.ROOT),
            entry.getEntryText()
        );
    }
}