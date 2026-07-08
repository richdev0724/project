package com.marketmono.entity;

/**
 * Enum representing the source of flow alert data
 */
public enum DataSourceEnum {
    WEBSOCKET("WebSocket"),
    REST("REST API"),
    KAFKA("Kafka");
    
    private final String displayName;
    
    DataSourceEnum(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
