package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for individual analyst rating data from upstream market data API
 */
public record AnalystRatingDto(
    @JsonProperty("action") String action,
    @JsonProperty("analyst_name") String analystName,
    @JsonProperty("firm") String firm,
    @JsonProperty("recommendation") String recommendation,
    @JsonProperty("sector") String sector,
    @JsonProperty("target") String target,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("timestamp") String timestamp
) {}




