package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stock or ETF holding in a politician's portfolio
 * Holdings are reported as ranges (min/mid/max amounts)
 */
public record StockHoldingDto(
    @JsonProperty("min_amount")
    Double minAmount,
    
    @JsonProperty("mid_amount")
    Double midAmount,
    
    @JsonProperty("max_amount")
    Double maxAmount,
    
    String ticker,
    String type                        // "stock" or "etf"
) {}
