package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Politician trade data (Senate/House stock transactions)
 * Matches upstream market data API "Senate Stock" schema from /api/politician-portfolios/recent_trades
 * Enhanced with party affiliation from local database
 */
public record PoliticianTradeDto(
    String amounts,                    // e.g., "$15,001 - $50,000"
    
    @JsonProperty("filed_at_date")
    LocalDate filedAtDate,
    
    @JsonProperty("is_active")
    Boolean isActive,
    
    String issuer,                     // "joint", "self", "spouse", "not-disclosed"
    
    @JsonProperty("member_type")
    String memberType,                 // "house" or "senate"
    
    String name,                       // Politician's full name
    String notes,
    String party,                      // Party code: D, R, I, UNK (enriched from database)
    
    @JsonProperty("politician_id")
    UUID politicianId,
    
    String reporter,
    String ticker,
    
    @JsonProperty("transaction_date")
    LocalDate transactionDate,
    
    @JsonProperty("txn_type")
    String txnType                     // "Buy", "Sell", "Exchange"
) {}
