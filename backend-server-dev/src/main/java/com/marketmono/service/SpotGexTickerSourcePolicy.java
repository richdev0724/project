package com.marketmono.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpotGexTickerSourcePolicy {

    private static final Set<String> REST_ONLY_TICKERS = Set.of("SPX", "SPXW", "VIX", "VIXW");

    private final StrataTickerFamilyResolver strataTickerFamilyResolver;

    public boolean isRestOnlyTicker(String ticker) {
        return REST_ONLY_TICKERS.contains(normalize(ticker));
    }

    public List<String> streamEligibleTickers(Collection<String> configuredTickers) {
        return normalizedDistinct(configuredTickers).stream()
            .filter(ticker -> !isRestOnlyTicker(ticker))
            .toList();
    }

    public List<String> restOnlyDisplayTickers(Collection<String> configuredTickers) {
        return normalizedDistinct(configuredTickers).stream()
            .filter(this::isRestOnlyTicker)
            .toList();
    }

    public List<String> collectionRootsForConfiguredTickers(Collection<String> configuredTickers) {
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        for (String ticker : restOnlyDisplayTickers(configuredTickers)) {
            roots.addAll(collectionRootsForConfiguredTicker(ticker));
        }
        return List.copyOf(roots);
    }

    public List<String> collectionRootsForConfiguredTicker(String ticker) {
        String normalizedTicker = normalize(ticker);
        if (!isRestOnlyTicker(normalizedTicker)) {
            return List.of();
        }
        return strataTickerFamilyResolver.spotExposureRoots(normalizedTicker);
    }

    public List<String> storageRootsForDisplayTicker(String ticker) {
        String normalizedTicker = normalize(ticker);
        if (!isRestOnlyTicker(normalizedTicker)) {
            return List.of(normalizedTicker);
        }
        return strataTickerFamilyResolver.spotExposureRoots(normalizedTicker);
    }

    public boolean isRestOnlyFamilyConfigured(String displayTicker, Collection<String> configuredTickers) {
        String normalizedTicker = normalize(displayTicker);
        if (!isRestOnlyTicker(normalizedTicker)) {
            return false;
        }
        Set<String> configured = new LinkedHashSet<>(normalizedDistinct(configuredTickers));
        return storageRootsForDisplayTicker(normalizedTicker).stream().anyMatch(configured::contains);
    }

    private List<String> normalizedDistinct(Collection<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }
        return tickers.stream()
            .filter(Objects::nonNull)
            .map(SpotGexTickerSourcePolicy::normalize)
            .filter(ticker -> !ticker.isBlank())
            .distinct()
            .toList();
    }

    private static String normalize(String ticker) {
        return ticker == null ? "" : ticker.trim().toUpperCase(Locale.US);
    }
}
