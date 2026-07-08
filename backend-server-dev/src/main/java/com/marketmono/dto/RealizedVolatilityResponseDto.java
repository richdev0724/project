package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for Realized Volatility data
 * Contains a list of realized volatility data points over a time period
 */
public record RealizedVolatilityResponseDto(
    @JsonProperty("data")
    List<RealizedVolatilityDto> data
) {}
