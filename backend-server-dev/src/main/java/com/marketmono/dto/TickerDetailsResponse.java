package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * STUB Response DTO for ticker details/company profile endpoint
 * 
 * Minimal stub implementation returning only ticker symbol when company metadata is unavailable.
 * 
 * Missing fields:
 * - Company name, description
 * - Logo/icon URLs  
 * - Total employees
 * - Market cap
 * - Address, headquarters
 * - Homepage URL
 * 
 * TODO: Integrate external data source if company metadata becomes critical
 * (e.g., FMP API, Yahoo Finance API, SEC EDGAR API)
 */
public record TickerDetailsResponse(
    @JsonProperty("ticker")
    String ticker,
    
    @JsonProperty("name")
    String name,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("logo_url")
    String logoUrl,
    
    @JsonProperty("icon_url")
    String iconUrl,
    
    @JsonProperty("total_employees")
    Integer totalEmployees,
    
    @JsonProperty("market_cap")
    Long marketCap,
    
    @JsonProperty("address")
    String address,
    
    @JsonProperty("is_etf")
    Boolean isEtf,
    
    @JsonProperty("homepage_url")
    String homepageUrl,
    
    @JsonProperty("note")
    String note
) {
    /**
     * Create stub ticker details response
     * Returns minimal data with null fields for unavailable company metadata
     * 
     * @param ticker Stock ticker symbol
     * @return Stub ticker details response
     */
    public static TickerDetailsResponse createStub(String ticker) {
        return new TickerDetailsResponse(
            ticker,
            null,  // name
            null,  // description
            null,  // logoUrl
            null,  // iconUrl
            null,  // totalEmployees
            null,  // marketCap
            null,  // address
            null,  // isEtf - could be inferred from ticker patterns, but keeping null for now
            null,  // homepageUrl
            "Company metadata is not available for this ticker."
        );
    }
}
