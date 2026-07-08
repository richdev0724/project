package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for analyst ratings endpoint
 */
public record AnalystRatingsResponseDto(
    @JsonProperty("data") List<AnalystRatingDto> data
) {}




