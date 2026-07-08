package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;

/**
 * DTO for upstream market data API flow alerts response
 * 
 * Note: Strike price is extracted from the option_chain field if not directly provided
 * Example option_chain: "SPY240105C00480000" -> strike = 480.00
 */
@JsonDeserialize(using = FlowAlertDeserializer.class)
public record UnusualWhalesFlowAlertDto(
    String id,
    String ticker,
    
    @JsonProperty("option_chain")
    String optionChain,
    
    BigDecimal strike,
    LocalDate expiry,
    String type,
    BigDecimal price,
    BigDecimal bid,
    BigDecimal ask,
    
    @JsonProperty("underlying_price")
    BigDecimal underlyingPrice,
    
    @JsonProperty("total_size")
    Integer totalSize,
    
    @JsonProperty("total_premium")
    BigDecimal totalPremium,
    
    @JsonProperty("trade_count")
    Integer tradeCount,
    
    Integer volume,
    
    @JsonProperty("open_interest")
    Integer openInterest,
    
    @JsonProperty("volume_oi_ratio")
    BigDecimal volumeOiRatio,
    
    @JsonProperty("iv_start")
    BigDecimal ivStart,
    
    @JsonProperty("iv_end")
    BigDecimal ivEnd,
    
    @JsonProperty("alert_rule")
    String alertRule,
    
    @JsonProperty("has_sweep")
    Boolean hasSweep,
    
    @JsonProperty("has_floor")
    Boolean hasFloor,
    
    @JsonProperty("has_singleleg")
    Boolean hasSingleleg,
    
    @JsonProperty("has_multileg")
    Boolean hasMultileg,
    
    @JsonProperty("all_opening_trades")
    Boolean allOpeningTrades,
    
    String sector,
    
    @JsonProperty("marketcap")
    BigDecimal marketCap,  // API returns decimal string like "377.681157"
    
    @JsonProperty("next_earnings_date")
    LocalDate nextEarningsDate,
    
    @JsonProperty("start_time")
    Long startTime,
    
    @JsonProperty("end_time")
    Long endTime,
    
    @JsonProperty("total_ask_side_prem")
    BigDecimal totalAskSidePremium,
    
    @JsonProperty("total_bid_side_prem")
    BigDecimal totalBidSidePremium,
    
    @JsonProperty("created_at")
    Instant createdAt,
    
    @JsonProperty("rule_id")
    String ruleId,
    
    @JsonProperty("expiry_count")
    Integer expiryCount,
    
    @JsonProperty("er_time")
    String erTime,
    
    @JsonProperty("issue_type")
    String issueType,
    
    @JsonProperty("missing_periscope")
    Boolean missingPeriscope
) {
    /**
     * Extract strike price from option_chain if not provided
     * Format: "SPY240105C00480000" -> strike = 480.00
     */
    public static BigDecimal extractStrikeFromOptionChain(String optionChain) {
        if (optionChain == null || optionChain.length() < 10) {
            return null;
        }
        
        try {
            // Option chain format: TICKER + EXPIRY_DATE + CALL/PUT + STRIKE
            // Example: SPY240105C00480000
            // The last 8 digits are the strike * 1000
            String strikeStr = optionChain.substring(optionChain.length() - 8);
            long strikeLong = Long.parseLong(strikeStr);
            return new BigDecimal(strikeLong).divide(new BigDecimal(1000), 2, java.math.RoundingMode.HALF_UP);
        } catch (Exception _) {
            return null;
        }
    }
}
