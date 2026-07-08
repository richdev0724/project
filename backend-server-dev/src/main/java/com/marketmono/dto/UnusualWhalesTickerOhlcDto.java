package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record UnusualWhalesTickerOhlcDto(
    List<TickerOhlcItem> data
) {
    public record TickerOhlcItem(
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        @JsonProperty("open") String open,
        @JsonProperty("high") String high,
        @JsonProperty("low") String low,
        @JsonProperty("close") String close,
        @JsonProperty("volume") Long volume,
        @JsonProperty("total_volume") Long totalVolume,
        @JsonProperty("market_time") String marketTime
    ) {}
}
