package com.marketmono.service;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;

public record StrataStockPriceTickerUniverse(
    List<StrataTickerMapping> mappings,
    StrataTickerUniverseProvenance provenance
) {

    public StrataStockPriceTickerUniverse {
        mappings = List.copyOf(mappings);
    }

    public List<String> displayTickers() {
        return mappings.stream()
            .map(StrataTickerMapping::displayTicker)
            .distinct()
            .toList();
    }

    public List<String> sourceTickers() {
        LinkedHashSet<String> sourceTickers = new LinkedHashSet<>();
        mappings.stream()
            .map(StrataTickerMapping::sourceTicker)
            .forEach(sourceTickers::add);
        return new ArrayList<>(sourceTickers);
    }
}
