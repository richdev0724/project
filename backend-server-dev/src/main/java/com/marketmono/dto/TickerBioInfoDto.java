package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TickerBioInfoDto(
    @JsonProperty("Ticker") String ticker,
    @JsonProperty("Name") String name,
    @JsonProperty("Sector") String sector,
    @JsonProperty("Bio") String bio
) {}
