package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.math.BigDecimal;

/**
 * WebSocket DTO for broadcasting complete flow alert data to frontend.
 * Includes all fields required by the flow table with proper formatting.
 */
public record WhaleFlowAlertDto(
    String id,
    String ticker,
    
    @JsonProperty("contract_id")
    String contractId,
    
    BigDecimal strike,
    String expiry,
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
    BigDecimal marketCap,
    
    @JsonProperty("next_earnings_date")
    String nextEarningsDate,
    
    @JsonProperty("trade_start_time")
    Instant tradeStartTime,
    
    @JsonProperty("trade_end_time")
    Instant tradeEndTime,
    
    @JsonProperty("total_ask_side_premium")
    BigDecimal totalAskSidePremium,
    
    @JsonProperty("total_bid_side_premium")
    BigDecimal totalBidSidePremium,
    
    @JsonProperty("market_session")
    String marketSession,
    
    @JsonProperty("trade_date_formatted")
    String tradeDateFormatted
) {}
