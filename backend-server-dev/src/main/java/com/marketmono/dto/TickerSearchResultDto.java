package com.marketmono.dto;

public record TickerSearchResultDto(
    String symbol,
    String name,
    String exchange,
    String exchangeShortName,
    String type,
    String country,
    String currency,
    boolean isActivelyTrading
) {}
