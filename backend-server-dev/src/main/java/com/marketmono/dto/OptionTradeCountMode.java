package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Controls whether Flow pagination returns an exact total count or a lower-cost
 * slice-style response. Missing/blank query values default to slice so hot
 * filter paths do not accidentally trigger count queries.
 */
public enum OptionTradeCountMode {

    EXACT("exact"),
    SLICE("slice");

    private final String wireValue;

    OptionTradeCountMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public static OptionTradeCountMode fromQueryParam(String value) {
        if (value == null || value.isBlank()) {
            return SLICE;
        }

        return Arrays.stream(values())
            .filter(mode -> mode.wireValue.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported countMode: " + value));
    }
}
