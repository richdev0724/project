package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * DTO for individual dark pool trade from upstream market data API
 * Contains comprehensive trade details including execution data and NBBO quotes
 */
public record DarkPoolRecentTradeDto(
    String ticker,
    
    BigDecimal price,
    
    Integer size,
    
    Integer volume,
    
    @JsonProperty("executed_at")
    String executedAt,
    
    BigDecimal premium,
    
    @JsonProperty("nbbo_ask")
    BigDecimal nbboAsk,
    
    @JsonProperty("nbbo_bid")
    BigDecimal nbboBid,
    
    @JsonProperty("nbbo_ask_quantity")
    Integer nbboAskQuantity,
    
    @JsonProperty("nbbo_bid_quantity")
    Integer nbboBidQuantity,
    
    @JsonProperty("market_center")
    String marketCenter,
    
    Boolean canceled,
    
    @JsonProperty("ext_hour_sold_codes")
    String extHourSoldCodes,
    
    @JsonProperty("sale_cond_codes")
    String saleCondCodes,
    
    @JsonProperty("tracking_id")
    Long trackingId,
    
    @JsonProperty("trade_code")
    String tradeCode,
    
    @JsonProperty("trade_settlement")
    String tradeSettlement
) {
}

