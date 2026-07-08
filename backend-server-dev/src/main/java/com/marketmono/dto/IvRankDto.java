package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for IV Rank data point
 * IV rank is a measure of where current implied volatility stands relative to its historical range
 */
public record IvRankDto(
    @JsonProperty("close")
    String close,
    
    @JsonProperty("date")
    String date,
    
    @JsonProperty("updated_at")
    String updatedAt,
    
    @JsonProperty("volatility")
    String volatility,
    
    @JsonProperty("iv_rank_1y")
    String ivRank1y
) {}
