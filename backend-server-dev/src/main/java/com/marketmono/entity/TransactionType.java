package com.marketmono.entity;

/**
 * Enum representing politician trade transaction types
 */
public enum TransactionType {
    BUY("Buy"),
    SELL("Sell"),
    EXCHANGE("Exchange");
    
    private final String displayName;
    
    TransactionType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Parse from API response value (case-insensitive)
     */
    public static TransactionType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        
        for (TransactionType type : values()) {
            if (type.displayName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unknown transaction type: " + value);
    }
}
