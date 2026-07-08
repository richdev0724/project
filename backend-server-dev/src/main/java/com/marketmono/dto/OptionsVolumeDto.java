package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for upstream market data provider /api/stock/{ticker}/options-volume endpoint response
 * Returns the options volume & premium for all trade executions
 * that happened on a given trading date for the given ticker.
 */
public record OptionsVolumeDto(
    @JsonProperty("data")
    List<OptionsVolumeItemDto> data
) {}





