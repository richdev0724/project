package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for individual insider transaction from upstream market data API
 * Represents aggregated insider trades by the same person on the same day
 */
public record InsiderTransactionDto(
    Long amount,
    
    @JsonProperty("date_excercisable")
    String dateExcercisable,
    
    @JsonProperty("director_indirect")
    String directorIndirect,
    
    @JsonProperty("expiration_date")
    String expirationDate,
    
    @JsonProperty("filing_date")
    String filingDate,
    
    String formtype,
    
    String id,
    
    List<String> ids,
    
    @JsonProperty("is_10b5_1")
    Boolean is10b51,
    
    @JsonProperty("is_director")
    Boolean isDirector,
    
    @JsonProperty("is_officer")
    Boolean isOfficer,
    
    @JsonProperty("is_s_p_500")
    Boolean isSP500,
    
    @JsonProperty("is_ten_percent_owner")
    Boolean isTenPercentOwner,
    
    String marketcap,
    
    String natureofownership,
    
    @JsonProperty("next_earnings_date")
    String nextEarningsDate,
    
    @JsonProperty("officer_title")
    String officerTitle,
    
    @JsonProperty("owner_name")
    String ownerName,
    
    String price,
    
    @JsonProperty("price_excercisable")
    String priceExcercisable,
    
    String sector,
    
    @JsonProperty("security_ad_code")
    String securityAdCode,
    
    @JsonProperty("security_title")
    String securityTitle,
    
    @JsonProperty("shares_owned_after")
    Long sharesOwnedAfter,
    
    @JsonProperty("shares_owned_before")
    Long sharesOwnedBefore,
    
    @JsonProperty("stock_price")
    String stockPrice,
    
    String ticker,
    
    @JsonProperty("transaction_code")
    String transactionCode,
    
    @JsonProperty("transaction_date")
    String transactionDate,
    
    Integer transactions
) {
}





