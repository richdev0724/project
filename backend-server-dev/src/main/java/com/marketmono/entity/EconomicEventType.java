package com.marketmono.entity;

/**
 * Economic event types from upstream market data API
 * Maps to PostgreSQL enum economic_event_type
 */
public enum EconomicEventType {
    FED_SPEAKER("fed-speaker"),
    FOMC("fomc"),
    REPORT("report"),
    SEC_13F("13F"),
    INDEX_REBALANCE("index-rebalance"),
    INDEX_REBALANCE_ANNOUNCEMENT("index-rebalance-announcement");
    
    private final String value;
    
    EconomicEventType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse from API string value (case-insensitive)
     */
    public static EconomicEventType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("EconomicEventType cannot be null");
        }
        return switch (value.toLowerCase()) {
            case "fed-speaker" -> FED_SPEAKER;
            case "fomc" -> FOMC;
            case "report" -> REPORT;
            case "13f" -> SEC_13F;
            case "index-rebalance" -> INDEX_REBALANCE;
            case "index-rebalance-announcement" -> INDEX_REBALANCE_ANNOUNCEMENT;
            default -> throw new IllegalArgumentException("Unknown EconomicEventType: " + value);
        };
    }
}
