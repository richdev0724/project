package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Realized Volatility data point
 * Contains implied volatility and realized volatility comparison data
 */
public record RealizedVolatilityDto(
    @JsonProperty("date")
    String date,
    
    @JsonProperty("price")
    String price,
    
    @JsonProperty("implied_volatility")
    String impliedVolatility,
    
    @JsonProperty("realized_volatility")
    String realizedVolatility,
    
    @JsonProperty("unshifted_rv_date")
    String unshiftedRvDate
) {}
