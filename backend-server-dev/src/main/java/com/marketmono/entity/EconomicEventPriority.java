package com.marketmono.entity;

/**
 * Priority levels for economic events based on market impact
 */
public enum EconomicEventPriority {
    HIGH,
    MEDIUM,
    LOW;
    
    public String getValue() {
        return name();
    }
}
