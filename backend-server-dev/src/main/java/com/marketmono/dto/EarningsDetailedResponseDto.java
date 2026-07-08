package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for detailed earnings endpoints (premarket/afterhours)
 * Contains list of detailed earnings items
 */
public record EarningsDetailedResponseDto(
    
    @JsonProperty("data")
    List<EarningsDetailedItemDto> data
) {
}



