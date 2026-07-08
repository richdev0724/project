package com.marketmono.service;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class StrataTickerPolicy {

    private StrataTickerPolicy() {
    }

    public static StrataTickerMapping resolve(String ticker) {
        String displayTicker = normalize(ticker);
        return new StrataTickerMapping(displayTicker, sourceTicker(displayTicker));
    }

    public static String sourceTicker(String displayTicker) {
        return switch (normalize(displayTicker)) {
            case "SPXW" -> "SPX";
            case "VIXW" -> "VIX";
            default -> normalize(displayTicker);
        };
    }

    public static String normalize(String ticker) {
        if (!StringUtils.hasText(ticker)) {
            throw new IllegalArgumentException("Ticker cannot be blank");
        }

        String normalized = ticker.trim().toUpperCase(Locale.US);
        if (normalized.length() > 10 || !normalized.matches("^[A-Z][A-Z0-9.\\-]{0,9}$")) {
            throw new IllegalArgumentException("Invalid ticker format: " + normalized);
        }
        return normalized;
    }
}
