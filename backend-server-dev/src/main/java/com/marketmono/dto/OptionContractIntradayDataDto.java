package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a single minute tick of intraday data for an option contract
 * Represents data from the upstream market data API endpoint:
 * GET /api/option-contract/{id}/intraday
 */
public record OptionContractIntradayDataDto(
    String close,
    String high,
    String low,
    String open,
    @JsonProperty("start_time") String startTime,
    @JsonProperty("avg_price") String avgPrice,
    @JsonProperty("iv_high") String ivHigh,
    @JsonProperty("iv_low") String ivLow,
    @JsonProperty("premium_ask_side") Long premiumAskSide,
    @JsonProperty("premium_bid_side") Long premiumBidSide,
    @JsonProperty("premium_mid_side") Long premiumMidSide,
    @JsonProperty("premium_no_side") Long premiumNoSide,
    @JsonProperty("volume_ask_side") Integer volumeAskSide,
    @JsonProperty("volume_bid_side") Integer volumeBidSide,
    @JsonProperty("volume_mid_side") Integer volumeMidSide,
    @JsonProperty("volume_no_side") Integer volumeNoSide,
    @JsonProperty("volume_multi") Integer volumeMulti,
    @JsonProperty("volume_stock_multi") Integer volumeStockMulti
) {
}



