package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for the stock information endpoint.
 * Contains detailed stock information including company details, market cap, sector, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockInformationResponse(
    @JsonProperty("data")
    StockInformationData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockInformationData(
        @JsonProperty("announce_time")
        String announceTime,           // Earnings announcement time preference
        
        @JsonProperty("avg30_volume")
        String avg30Volume,            // 30-day average trading volume
        
        @JsonProperty("full_name")
        String fullName,               // Full company name
        
        @JsonProperty("has_dividend")
        Boolean hasDividend,           // Whether the stock pays dividends
        
        @JsonProperty("has_earnings_history")
        Boolean hasEarningsHistory,    // Whether earnings history is available
        
        @JsonProperty("has_investment_arm")
        Boolean hasInvestmentArm,      // Whether company has investment arm
        
        @JsonProperty("has_options")
        Boolean hasOptions,            // Whether options are available
        
        @JsonProperty("issue_type")
        String issueType,              // Type of security (e.g., "Common Stock")
        
        @JsonProperty("marketcap")
        String marketcap,              // Market capitalization
        
        @JsonProperty("marketcap_size")
        String marketcapSize,          // Market cap category (e.g., "big", "mid", "small")
        
        @JsonProperty("next_earnings_date")
        String nextEarningsDate,       // Next earnings report date (YYYY-MM-DD)
        
        @JsonProperty("sector")
        String sector,                 // Company sector
        
        @JsonProperty("short_description")
        String shortDescription,       // Brief company description
        
        @JsonProperty("symbol")
        String symbol,                 // Stock ticker symbol
        
        @JsonProperty("source_tags")
        @JsonAlias("uw_tags")
        List<String> sourceTags        // Provider tags
    ) {}
}


