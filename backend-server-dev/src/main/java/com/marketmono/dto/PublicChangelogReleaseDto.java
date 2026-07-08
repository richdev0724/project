package com.marketmono.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Public changelog release grouping exposed on the Help page.
 */
public record PublicChangelogReleaseDto(
    String version,
    LocalDate date,
    List<PublicChangelogChangeDto> changes
) {}