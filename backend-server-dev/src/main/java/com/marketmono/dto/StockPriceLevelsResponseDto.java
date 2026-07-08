package com.marketmono.dto;

import java.util.List;

/**
 * Wrapper for options price levels API response
 */
public record StockPriceLevelsResponseDto(
    List<StockPriceLevelDto> data
) {}
