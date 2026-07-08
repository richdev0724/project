package com.marketmono.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for creating a changelog entry.
 */
public record CreateChangelogEntryRequest(
    @NotBlank(message = "Release version is required")
    @Size(max = 50, message = "Release version must be at most 50 characters")
    String releaseVersion,

    @NotNull(message = "Release date is required")
    LocalDate releaseDate,

    @NotBlank(message = "Entry type is required")
    @Size(max = 20, message = "Entry type must be at most 20 characters")
    String entryType,

    @NotBlank(message = "Entry text is required")
    @Size(max = 2000, message = "Entry text must be at most 2000 characters")
    String entryText,

    @NotNull(message = "Sort order is required")
    @Min(value = 0, message = "Sort order must be zero or greater")
    Integer sortOrder,

    @NotBlank(message = "Visibility is required")
    @Size(max = 20, message = "Visibility must be at most 20 characters")
    String visibility
) {}