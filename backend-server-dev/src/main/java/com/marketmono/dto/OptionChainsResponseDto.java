package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for option chains endpoint
 * Returns all option symbols for a given ticker
 */
public record OptionChainsResponseDto(
    @JsonProperty("data") List<String> data
) {}




