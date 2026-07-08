package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record TickerTideDataPoint(
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("date") String date,
    @JsonProperty("call_premium") BigDecimal callPremium,
    @JsonProperty("put_premium") BigDecimal putPremium,
    @JsonProperty("net_volume") Integer netVolume,
    @JsonProperty("ticker_close") BigDecimal tickerClose,
    @JsonProperty("original_timestamp") String originalTimestamp,
    @JsonProperty("net_delta") BigDecimal netDelta,
    @JsonProperty("call_volume") Integer callVolume,
    @JsonProperty("put_volume") Integer putVolume
) {}
