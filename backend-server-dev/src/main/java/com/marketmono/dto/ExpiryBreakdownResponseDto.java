package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExpiryBreakdownResponseDto(
    @JsonProperty("data") List<ExpiryBreakdownDto> data
) {}
