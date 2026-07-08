package com.marketmono.dto;

import java.util.List;

public record StrataStockPriceLatestResponse(
    List<StrataStockPriceLatestDto> prices,
    List<StrataStockPriceMissingDto> missing
) {
    public StrataStockPriceLatestResponse {
        prices = List.copyOf(prices);
        missing = List.copyOf(missing);
    }
}
