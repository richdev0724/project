package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for upstream market data provider /stock/:ticker/max-pain endpoint.
 * The frontend consumes a list of expiry rows with `max_pain` values and an optional date marker.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockMaxPainResponse(
    @JsonProperty("data")
    List<StockMaxPainRow> data,

    @JsonProperty("date")
    String date
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockMaxPainRow(
        @JsonProperty("expiry")
        String expiry,

        @JsonProperty("max_pain")
        String maxPain
    ) {}
}