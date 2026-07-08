package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Individual item in options volume response
 * Contains daily aggregated volume and premium data for calls and puts
 */
public record OptionsVolumeItemDto(
    @JsonProperty("date")
    LocalDate date,
    
    @JsonProperty("call_volume")
    Long callVolume,
    
    @JsonProperty("put_volume")
    Long putVolume,
    
    @JsonProperty("call_premium")
    String callPremium,
    
    @JsonProperty("put_premium")
    String putPremium,
    
    @JsonProperty("call_open_interest")
    Long callOpenInterest,
    
    @JsonProperty("put_open_interest")
    Long putOpenInterest,
    
    @JsonProperty("call_volume_ask_side")
    Long callVolumeAskSide,
    
    @JsonProperty("call_volume_bid_side")
    Long callVolumeBidSide,
    
    @JsonProperty("put_volume_ask_side")
    Long putVolumeAskSide,
    
    @JsonProperty("put_volume_bid_side")
    Long putVolumeBidSide,
    
    @JsonProperty("bullish_premium")
    String bullishPremium,
    
    @JsonProperty("bearish_premium")
    String bearishPremium,
    
    @JsonProperty("net_call_premium")
    String netCallPremium,
    
    @JsonProperty("net_put_premium")
    String netPutPremium,
    
    @JsonProperty("avg_3_day_call_volume")
    String avg3DayCallVolume,
    
    @JsonProperty("avg_3_day_put_volume")
    String avg3DayPutVolume,
    
    @JsonProperty("avg_7_day_call_volume")
    String avg7DayCallVolume,
    
    @JsonProperty("avg_7_day_put_volume")
    String avg7DayPutVolume,
    
    @JsonProperty("avg_30_day_call_volume")
    String avg30DayCallVolume,
    
    @JsonProperty("avg_30_day_put_volume")
    String avg30DayPutVolume
) {}





