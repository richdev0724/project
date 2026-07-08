package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Individual congress trade record from upstream market data API
 */
public record CongressTrade(
    @JsonProperty("politician_id") UUID politician_id,
    String name,
    String ticker,
    String issuer,
    @JsonProperty("is_active") Boolean is_active,
    String reporter,
    @JsonProperty("txn_type") String txn_type,
    String amounts,
    String notes,
    @JsonProperty("transaction_date") LocalDate transaction_date,
    @JsonProperty("filed_at_date") LocalDate filed_at_date,
    @JsonProperty("member_type") String member_type
) {
    // Convenience methods for entity mapping (snake_case -> camelCase)
    public UUID politicianId() { return politician_id; }
    public Boolean isActive() { return is_active; }
    public String txnType() { return txn_type; }
    public LocalDate transactionDate() { return transaction_date; }
    public LocalDate filedAtDate() { return filed_at_date; }
    public String memberType() { return member_type; }
}
