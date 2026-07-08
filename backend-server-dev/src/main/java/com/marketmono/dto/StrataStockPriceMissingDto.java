package com.marketmono.dto;

public record StrataStockPriceMissingDto(
    String displayTicker,
    String sourceTicker,
    String reason
) {
}
