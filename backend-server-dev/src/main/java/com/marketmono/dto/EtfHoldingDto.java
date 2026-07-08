package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EtfHoldingDto(
    String close,
    String high,
    String low,
    String name,
    String open,
    String type,
    String ticker,
    String updated,
    String weight,

    @JsonProperty("country_code")
    String countryCode,

    String volume,
    String sector,
    String etf,

    @JsonProperty("call_volume")
    Long callVolume,

    @JsonProperty("put_volume")
    Long putVolume,

    @JsonProperty("has_options")
    Boolean hasOptions,

    @JsonProperty("put_premium")
    String putPremium,

    @JsonProperty("call_premium")
    String callPremium,

    @JsonProperty("week52_high")
    String week52High,

    @JsonProperty("week52_low")
    String week52Low,

    @JsonProperty("avg30_volume")
    String avg30Volume,

    @JsonProperty("bearish_premium")
    String bearishPremium,

    @JsonProperty("bullish_premium")
    String bullishPremium,

    @JsonProperty("short_name")
    String shortName,

    @JsonProperty("prev_price")
    String prevPrice,

    @JsonProperty("country_name")
    String countryName,

    String continent,
    String shares,
    String cusip,
    String isin
) {}
