package com.marketmono.service;

import java.util.List;

public record StrataStockPriceCollectionResult(
    List<String> successfulSourceTickers,
    List<String> failedSourceTickers,
    int rowsSaved
) {
    public StrataStockPriceCollectionResult {
        successfulSourceTickers = List.copyOf(successfulSourceTickers);
        failedSourceTickers = List.copyOf(failedSourceTickers);
    }
}
