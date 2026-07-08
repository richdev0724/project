package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for off-lit (dark pool) and lit (exchange) volume at a specific price level
 * Represents data from upstream market data API /api/stock/{ticker}/stock-volume-price-levels
 */
public record OffLitPriceLevelDto(
    String price,
    
    @JsonProperty("off_vol")
    long offVol,
    
    @JsonProperty("lit_vol")
    long litVol
) {}
