package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record UnusualWhalesTickerTideDto(
    List<TickerTideItem> data
) {
    public record TickerTideItem(
        @JsonProperty("tape_time") String tapeTime,
        @JsonProperty("date") String date,
        @JsonProperty("net_call_premium") BigDecimal netCallPremium,
        @JsonProperty("net_put_premium") BigDecimal netPutPremium,
        @JsonProperty("net_call_volume") Integer netCallVolume,
        @JsonProperty("net_put_volume") Integer netPutVolume
    ) {}
}
