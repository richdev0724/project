package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Greek exposure data aggregated by strike price
 * Maps to /api/stock/{ticker}/greek-exposure/strike response
 */
public record GreekExposureByStrikeDto(
    String date,
    BigDecimal strike,
    
    @JsonProperty("call_delta")
    BigDecimal callDelta,
    
    @JsonProperty("put_delta")
    BigDecimal putDelta,
    
    @JsonProperty("call_charm")
    BigDecimal callCharm,
    
    @JsonProperty("call_gex")
    BigDecimal callGex,
    
    @JsonProperty("call_vanna")
    BigDecimal callVanna,
    
    @JsonProperty("put_charm")
    BigDecimal putCharm,
    
    @JsonProperty("put_gex")
    BigDecimal putGex,
    
    @JsonProperty("put_vanna")
    BigDecimal putVanna,
    
    @JsonProperty("call_gamma")
    BigDecimal callGamma,
    
    @JsonProperty("put_gamma")
    BigDecimal putGamma
) {}
