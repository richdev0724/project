package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * DTO for individual option contract from upstream market data provider screener API
 */
public record OptionContractDto(
    @JsonProperty("is_new")
    Boolean isNew,
    
    BigDecimal gamma,
    BigDecimal vega,
    
    @JsonProperty("stock_multi_leg_volume")
    Integer stockMultiLegVolume,
    
    @JsonProperty("ticker_vol")
    Long tickerVol,
    
    @JsonProperty("floor_volume")
    Integer floorVolume,
    
    @JsonProperty("stock_price")
    String stockPrice,
    
    String open,
    BigDecimal premium,
    
    @JsonProperty("no_side_volume")
    Integer noSideVolume,
    
    @JsonProperty("bid_side_perc_7_day")
    String bidSidePerc7Day,
    
    @JsonProperty("avg_price")
    String avgPrice,
    
    @JsonProperty("option_symbol")
    String optionSymbol,
    
    @JsonProperty("ticker_symbol")
    String tickerSymbol,
    
    @JsonProperty("next_earnings_date")
    String nextEarningsDate,
    
    @JsonProperty("total_bid_changes")
    Integer totalBidChanges,
    
    @JsonProperty("option_watchlists")
    String optionWatchlists,
    
    @JsonProperty("bid_side_volume")
    Integer bidSideVolume,
    
    @JsonProperty("mid_volume")
    Integer midVolume,
    
    @JsonProperty("ask_side_volume")
    Integer askSideVolume,
    
    @JsonProperty("total_ask_changes")
    Integer totalAskChanges,
    
    BigDecimal theta,
    
    @JsonProperty("days_of_oi_increases")
    Integer daysOfOiIncreases,
    
    String low,
    String close,
    
    @JsonProperty("cross_volume")
    Integer crossVolume,
    
    @JsonProperty("ask_side_perc_7_day")
    String askSidePerc7Day,
    
    BigDecimal delta,
    
    @JsonProperty("last_fill")
    String lastFill,
    
    BigDecimal roc,
    Integer volume,
    
    @JsonProperty("sweep_volume")
    Integer sweepVolume,
    
    @JsonProperty("chain_prev_close")
    String chainPrevClose,
    
    @JsonProperty("days_of_vol_greater_than_oi")
    Integer daysOfVolGreaterThanOi,
    
    String sector,
    
    @JsonProperty("multileg_volume")
    Integer multilegVolume,
    
    @JsonProperty("prev_oi")
    Integer prevOi,
    
    String high,
    Integer trades,
    
    @JsonProperty("open_interest")
    Integer openInterest,
    
    @JsonProperty("er_time")
    String erTime,
    
    @JsonProperty("issue_type")
    String issueType
) {}


