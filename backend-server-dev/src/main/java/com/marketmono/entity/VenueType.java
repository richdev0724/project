package com.marketmono.entity;

public enum VenueType {
    DARK("dark"),
    LIT("lit");

    private final String value;

    VenueType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static VenueType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("VenueType cannot be null");
        }
        return switch (value.toLowerCase()) {
            case "dark" -> DARK;
            case "lit" -> LIT;
            default -> throw new IllegalArgumentException("Unknown VenueType: " + value);
        };
    }
}
