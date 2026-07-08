package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for IV Rank data
 * Contains a list of IV rank data points over a time period
 */
public record IvRankResponseDto(
    @JsonProperty("data")
    List<IvRankDto> data
) {}
