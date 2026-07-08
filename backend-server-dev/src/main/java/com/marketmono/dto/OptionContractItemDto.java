package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a single option contract item
 * Used in the response from /api/stock/{ticker}/option-contracts
 */
public record OptionContractItemDto(
    Integer volume,
    @JsonProperty("implied_volatility") String impliedVolatility,
    @JsonProperty("open_interest") Integer openInterest,
    @JsonProperty("option_symbol") String optionSymbol,
    @JsonProperty("bid_volume") Integer bidVolume,
    @JsonProperty("ask_volume") Integer askVolume,
    @JsonProperty("mid_volume") Integer midVolume,
    @JsonProperty("avg_price") String avgPrice,
    @JsonProperty("prev_oi") Integer prevOi,
    @JsonProperty("nbbo_ask") String nbboAsk,
    @JsonProperty("nbbo_bid") String nbboBid,
    @JsonProperty("last_price") String lastPrice,
    @JsonProperty("floor_volume") Integer floorVolume,
    @JsonProperty("high_price") String highPrice,
    @JsonProperty("low_price") String lowPrice,
    @JsonProperty("multi_leg_volume") Integer multiLegVolume,
    @JsonProperty("stock_multi_leg_volume") Integer stockMultiLegVolume,
    @JsonProperty("sweep_volume") Integer sweepVolume,
    @JsonProperty("total_premium") String totalPremium,
    @JsonProperty("no_side_volume") Integer noSideVolume,
    @JsonProperty("cross_volume") Integer crossVolume
) {
}



