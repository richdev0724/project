package com.marketmono.entity;

/**
 * Enum representing who issued/owns the security in a politician trade
 * Maps to PostgreSQL issuer_type enum
 */
public enum IssuerType {
    SELF("self"),
    SPOUSE("spouse"),
    CHILD("child"),
    JOINT("joint"),
    UNDISCLOSED("not-disclosed");
    
    private final String apiValue;
    
    IssuerType(String apiValue) {
        this.apiValue = apiValue;
    }
    
    public String getApiValue() {
        return apiValue;
    }
    
    /**
     * Parse from API response value (case-insensitive)
     * Supports both enum names (SELF, SPOUSE, etc.) and API values (self, spouse, not-disclosed, etc.)
     * 
     * @param value the string value from API or UI
     * @return IssuerType enum, or null if value is null/blank
     * @throws IllegalArgumentException if value doesn't match any issuer type
     */
    public static IssuerType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        
        String normalized = value.trim();
        
        // Try exact enum name match first (SELF, SPOUSE, CHILD, JOINT, UNDISCLOSED)
        for (IssuerType type : values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        
        // Try API value match (self, spouse, child, joint, not-disclosed)
        for (IssuerType type : values()) {
            if (type.apiValue.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unknown issuer type: " + value);
    }
}
