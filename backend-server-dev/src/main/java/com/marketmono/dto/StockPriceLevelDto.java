package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Stock price level with call and put volume
 * Maps to /api/stock/{ticker}/option/stock-price-levels response
 */
public record StockPriceLevelDto(
    BigDecimal price,
    
    @JsonProperty("call_volume")
    Integer callVolume,
    
    @JsonProperty("put_volume")
    Integer putVolume
) {}
