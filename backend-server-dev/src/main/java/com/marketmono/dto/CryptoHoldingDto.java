package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cryptocurrency holding in a politician's portfolio
 */
public record CryptoHoldingDto(
    @JsonProperty("min_amount")
    Double minAmount,
    
    @JsonProperty("mid_amount")
    Double midAmount,
    
    @JsonProperty("max_amount")
    Double maxAmount,
    
    String symbol,                     // Crypto symbol (e.g., "BTC-USD")
    String type                        // "crypto"
) {}
