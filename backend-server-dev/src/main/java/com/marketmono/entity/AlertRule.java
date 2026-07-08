package com.marketmono.entity;

public enum AlertRule {
    LARGE_BLOCK("Large Block"),
    SWEEP_ORDER("Sweep Order"),
    FLOOR_TRADE("Floor Trade"),
    OPENING_POSITION("Opening Position"),
    UNUSUAL_ACTIVITY("Unusual Activity"),
    REPEATED_HITS("Repeated Hits"),
    REPEATED_HITS_ASCENDING_FILL("Repeated Hits Ascending Fill"),
    REPEATED_HITS_DESCENDING_FILL("Repeated Hits Descending Fill");
    
    private final String displayName;
    
    AlertRule(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
