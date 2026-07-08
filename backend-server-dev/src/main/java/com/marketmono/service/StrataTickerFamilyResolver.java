package com.marketmono.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Resolves display tickers to provider/storage ticker families for Strata index products.
 */
@Service
public class StrataTickerFamilyResolver {

    public String normalizeDisplayTicker(String ticker) {
        return ticker == null ? "" : ticker.trim().toUpperCase(Locale.US);
    }

    public List<String> optionChainRoots(String ticker) {
        return familyRoots(ticker);
    }

    public List<String> spotExposureRoots(String ticker) {
        return familyRoots(ticker);
    }

    public List<String> replayRoots(String ticker) {
        return familyRoots(ticker);
    }

    public List<String> expiryBreakdownRoots(String ticker) {
        String normalizedTicker = normalizeDisplayTicker(ticker);
        return switch (normalizedTicker) {
            case "VIX", "VIXW" -> dedupe(List.of("VIXW", "VIX"));
            default -> normalizedTicker.isBlank() ? List.of() : List.of(normalizedTicker);
        };
    }

    public String cacheKey(String ticker) {
        return String.join(",", familyRoots(ticker));
    }

    private List<String> familyRoots(String ticker) {
        String normalizedTicker = normalizeDisplayTicker(ticker);
        return switch (normalizedTicker) {
            case "SPX", "SPXW" -> dedupe(List.of("SPXW", "SPX"));
            case "VIX", "VIXW" -> dedupe(List.of("VIXW", "VIX"));
            default -> normalizedTicker.isBlank() ? List.of() : List.of(normalizedTicker);
        };
    }

    private static List<String> dedupe(List<String> tickers) {
        return List.copyOf(new LinkedHashSet<>(tickers));
    }
}
