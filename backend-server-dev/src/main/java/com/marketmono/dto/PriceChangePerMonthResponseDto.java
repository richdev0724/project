package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for price change per month endpoint
 * Returns relative price change for all past months over multiple years
 */
public record PriceChangePerMonthResponseDto(
    
    @JsonProperty("data")
    List<PriceChangePerMonthItemDto> data
) {
}



