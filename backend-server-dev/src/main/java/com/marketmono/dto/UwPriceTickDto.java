package com.marketmono.dto;

import java.math.BigDecimal;

/**
 * Single tick from the Unusual Whales {@code price:<TICKER>} websocket channel.
 * Payload format: {@code ["price:SPY",{"close":"562.82","time":1726670327692,"vol":6015555}]}
 *
 * @param ticker  ticker the channel is scoped to (from the channel name)
 * @param close   last trade price
 * @param timeMs  trade execution time, unix epoch milliseconds (UTC)
 * @param volume  cumulative session volume on the underlying as of timeMs
 */
public record UwPriceTickDto(
    String ticker,
    BigDecimal close,
    long timeMs,
    Long volume
) {}
