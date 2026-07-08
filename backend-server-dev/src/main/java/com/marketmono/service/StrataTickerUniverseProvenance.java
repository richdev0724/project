package com.marketmono.service;

public record StrataTickerUniverseProvenance(
    int fixedCount,
    int collectionConfigCount,
    int userPreferenceCount,
    int crmSavedFilterCount
) {
}
