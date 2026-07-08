package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for Greek exposure data aggregated by strike and expiry
 * From upstream market data provider /stock/{ticker}/greek-exposure/strike-expiry endpoint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GreekExposureByStrikeExpiryDto(
    @JsonProperty("date")
    String date,
    
    @JsonProperty("expiry")
    String expiry,
    
    @JsonProperty("strike")
    String strike,
    
    @JsonProperty("dte")
    Integer dte,
    
    @JsonProperty("call_delta")
    String callDelta,
    
    @JsonProperty("put_delta")
    String putDelta,
    
    @JsonProperty("call_charm")
    String callCharm,
    
    @JsonProperty("call_gex")
    String callGex,
    
    @JsonProperty("call_vanna")
    String callVanna,
    
    @JsonProperty("put_charm")
    String putCharm,
    
    @JsonProperty("put_gex")
    String putGex,
    
    @JsonProperty("put_vanna")
    String putVanna
) {}

