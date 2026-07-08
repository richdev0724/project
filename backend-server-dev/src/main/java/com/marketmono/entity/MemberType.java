package com.marketmono.entity;

/**
 * Enum representing politician's chamber of Congress
 */
public enum MemberType {
    HOUSE("house"),
    SENATE("senate");
    
    private final String value;
    
    MemberType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse from API response value (case-insensitive)
     */
    public static MemberType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        
        for (MemberType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("Unknown member type: " + value);
    }
}
