package com.marketmono.entity;

public enum OptionTypeEnum {
    CALL("Call"),
    PUT("Put");
    
    private final String displayName;
    
    OptionTypeEnum(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
