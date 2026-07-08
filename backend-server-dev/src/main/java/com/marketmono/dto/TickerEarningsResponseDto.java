package com.marketmono.dto;

import java.util.List;

/**
 * DTO for ticker earnings response from upstream market data API
 */
public record TickerEarningsResponseDto(
    List<TickerEarningsDataDto> data
) {}