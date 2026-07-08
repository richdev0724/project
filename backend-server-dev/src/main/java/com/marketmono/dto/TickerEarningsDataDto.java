package com.marketmono.dto;

/**
 * DTO for ticker earnings data from upstream market data API
 */
public record TickerEarningsDataDto(
    String actual_eps,
    String ending_fiscal_quarter,
    String expected_move,
    String expected_move_perc,
    String long_straddle_1d,
    String long_straddle_1w,
    String post_earnings_move_1d,
    String post_earnings_move_1w,
    String post_earnings_move_2w,
    String post_earnings_move_3d,
    String pre_earnings_move_1d,
    String pre_earnings_move_1w,
    String pre_earnings_move_2w,
    String pre_earnings_move_3d,
    String report_date,
    String report_time,
    String short_straddle_1d,
    String short_straddle_1w,
    String source,
    String street_mean_est
) {}