package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Greek exposure data aggregated by expiration date
 * Maps to /api/stock/{ticker}/greek-exposure/expiry response
 */
public record GreekExposureByExpiryDto(
    LocalDate expiry,
    
    Integer dte,
    
    @JsonProperty("call_gamma")
    BigDecimal callGamma,
    
    @JsonProperty("put_gamma")
    BigDecimal putGamma,
    
    @JsonProperty("call_delta")
    BigDecimal callDelta,
    
    @JsonProperty("put_delta")
    BigDecimal putDelta,
    
    @JsonProperty("call_charm")
    BigDecimal callCharm,
    
    @JsonProperty("put_charm")
    BigDecimal putCharm,
    
    @JsonProperty("call_vanna")
    BigDecimal callVanna,
    
    @JsonProperty("put_vanna")
    BigDecimal putVanna
) {}
