package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Option holding in a politician's portfolio
 */
public record OptionHoldingDto(
    @JsonProperty("min_amount")
    Double minAmount,
    
    @JsonProperty("mid_amount")
    Double midAmount,
    
    @JsonProperty("max_amount")
    Double maxAmount,
    
    String symbol,                     // Option contract symbol
    String type                        // "option"
) {}
