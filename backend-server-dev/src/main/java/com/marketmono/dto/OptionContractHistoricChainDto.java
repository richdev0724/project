package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a single day of historic data for an option contract
 * Represents data from the upstream market data API endpoint:
 * GET /api/option-contract/{id}/historic
 */
public record OptionContractHistoricChainDto(
    String date,
    Integer volume,
    @JsonProperty("implied_volatility") String impliedVolatility,
    @JsonProperty("open_interest") Integer openInterest,
    Integer trades,
    @JsonProperty("last_price") String lastPrice,
    @JsonProperty("ask_volume") Integer askVolume,
    @JsonProperty("bid_volume") Integer bidVolume,
    @JsonProperty("nbbo_bid") String nbboBid,
    @JsonProperty("nbbo_ask") String nbboAsk,
    @JsonProperty("avg_price") String avgPrice,
    @JsonProperty("iv_high") String ivHigh,
    @JsonProperty("iv_low") String ivLow,
    @JsonProperty("mid_volume") Integer midVolume,
    @JsonProperty("canceled_volume") Integer canceledVolume,
    @JsonProperty("cross_volume") Integer crossVolume,
    @JsonProperty("floor_volume") Integer floorVolume,
    @JsonProperty("high_price") String highPrice,
    @JsonProperty("last_tape_time") String lastTapeTime,
    @JsonProperty("low_price") String lowPrice,
    @JsonProperty("multi_leg_volume") Integer multiLegVolume,
    @JsonProperty("neutral_volume") Integer neutralVolume,
    @JsonProperty("open_price") String openPrice,
    @JsonProperty("stock_multi_leg_volume") Integer stockMultiLegVolume,
    @JsonProperty("sweep_volume") Integer sweepVolume,
    @JsonProperty("total_ask_changes") Integer totalAskChanges,
    @JsonProperty("total_bid_changes") Integer totalBidChanges,
    @JsonProperty("total_premium") String totalPremium,
    @JsonProperty("flex_oi_transfer") Integer flexOiTransfer,
    @JsonProperty("ticker_vol") Integer tickerVol
) {
}



