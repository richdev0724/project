package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record ExpiryBreakdownDto(
    @JsonProperty("expires") LocalDate expires,
    @JsonProperty("volume") Long volume,
    @JsonProperty("open_interest") Long openInterest,
    @JsonProperty("chains") Integer chains
) {}
