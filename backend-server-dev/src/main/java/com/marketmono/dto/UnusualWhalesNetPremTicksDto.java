package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for upstream market data provider /api/stock/{ticker}/net-prem-ticks endpoint response
 * Contains intraday net premium and volume data at each tape time
 */
public record UnusualWhalesNetPremTicksDto(
    List<NetPremTickItem> data
) {
    public record NetPremTickItem(
        String date,
        
        @JsonProperty("tape_time")
        String tapeTime,
        
        @JsonProperty("net_call_premium")
        String netCallPremium,
        
        @JsonProperty("net_put_premium")
        String netPutPremium,
        
        @JsonProperty("net_call_volume")
        Integer netCallVolume,
        
        @JsonProperty("net_put_volume")
        Integer netPutVolume
    ) {}
}
