package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record DarkPoolTopTradesResponseDto(
    List<DarkPoolRecentTradeDto> data,

    @JsonProperty("constituents_by_tracking_id")
    Map<String, List<DarkPoolRecentTradeDto>> constituentsByTrackingId
) {
}
