package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Detailed DTO for earnings item data from upstream market data API
 * Contains comprehensive earnings information including EPS, company details, and reaction data
 */
public record EarningsDetailedItemDto(
    
    @JsonProperty("actual_eps")
    String actualEps,
    
    @JsonProperty("continent")
    String continent,
    
    @JsonProperty("country_code")
    String countryCode,
    
    @JsonProperty("country_name")
    String countryName,
    
    @JsonProperty("ending_fiscal_quarter")
    String endingFiscalQuarter,
    
    @JsonProperty("expected_move")
    String expectedMove,
    
    @JsonProperty("expected_move_perc")
    String expectedMovePerc,
    
    @JsonProperty("full_name")
    String fullName,
    
    @JsonProperty("has_options")
    Boolean hasOptions,
    
    @JsonProperty("is_s_p_500")
    Boolean isSpAndP500,
    
    @JsonProperty("marketcap")
    String marketcap,
    
    @JsonProperty("post_earnings_close")
    String postEarningsClose,
    
    @JsonProperty("post_earnings_date")
    String postEarningsDate,
    
    @JsonProperty("pre_earnings_close")
    String preEarningsClose,
    
    @JsonProperty("pre_earnings_date")
    String preEarningsDate,
    
    @JsonProperty("reaction")
    String reaction,
    
    @JsonProperty("report_date")
    String reportDate,
    
    @JsonProperty("report_time")
    String reportTime,
    
    @JsonProperty("sector")
    String sector,
    
    @JsonProperty("source")
    String source,
    
    @JsonProperty("street_mean_est")
    String streetMeanEst,
    
    @JsonProperty("symbol")
    String symbol
) {
}

