package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DTO for individual price change per month data item
 * Represents the price change for a ticker in a specific month of a specific year
 */
public record PriceChangePerMonthItemDto(
    
    @JsonProperty("change")
    String change,
    
    @JsonProperty("close")
    BigDecimal close,
    
    @JsonProperty("month")
    Integer month,
    
    @JsonProperty("open")
    BigDecimal open,
    
    @JsonProperty("year")
    Integer year
) {
}



