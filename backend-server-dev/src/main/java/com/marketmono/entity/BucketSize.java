package com.marketmono.entity;

/**
 * Enum for time bucket sizes used in DP/LP aggregations.
 * IMPORTANT: Enum names must match PostgreSQL enum values exactly.
 */
public enum BucketSize {
    // Enum names MUST match PostgreSQL enum 'bucket_size' values
    ONE_MINUTE("1m"),    // Display value for APIs
    FIVE_MINUTES("5m"),
    ONE_HOUR("1h"),
    ONE_DAY("1d");

    private final String displayValue;

    BucketSize(String displayValue) {
        this.displayValue = displayValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * Parse from display value (e.g., "1m" → ONE_MINUTE).
     * Used for API input parsing.
     */
    public static BucketSize fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("BucketSize cannot be null");
        }
        return switch (value.toLowerCase()) {
            case "1m" -> ONE_MINUTE;
            case "5m" -> FIVE_MINUTES;
            case "1h" -> ONE_HOUR;
            case "1d" -> ONE_DAY;
            default -> throw new IllegalArgumentException("Unknown BucketSize: " + value);
        };
    }
}
