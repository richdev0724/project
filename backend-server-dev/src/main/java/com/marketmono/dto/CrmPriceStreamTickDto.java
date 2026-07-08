package com.marketmono.dto;

import java.math.BigDecimal;

/**
 * Live price tick relayed to the Strata frontend over STOMP
 * ({@code /topic/strata-prices/{ticker}}). Change fields are computed against
 * the daily 9:30 AM ET stock-state baseline (prev_close) for the ticker and
 * are null until a baseline exists.
 *
 * @param time unix epoch milliseconds (UTC) of the trade
 * @param vol  cumulative session volume as of the tick
 */
public record CrmPriceStreamTickDto(
    String ticker,
    BigDecimal price,
    BigDecimal prevClose,
    BigDecimal change,
    BigDecimal changePercent,
    long time,
    Long vol
) {}
