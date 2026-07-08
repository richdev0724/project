package com.marketmono.entity;

/**
 * Payment status enum matching PostgreSQL payment_status type.
 * Tracks the status of individual payment transactions.
 */
public enum PaymentStatus {
    /**
     * Payment was successfully completed.
     */
    PAID("paid"),
    
    /**
     * Payment attempt failed.
     */
    FAILED("failed"),
    
    /**
     * Payment is pending processing.
     */
    PENDING("pending"),
    
    /**
     * Payment was refunded to the customer.
     */
    REFUNDED("refunded");
    
    private final String value;
    
    PaymentStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse PaymentStatus from string value (case-insensitive).
     */
    public static PaymentStatus fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("PaymentStatus cannot be null");
        }
        return switch (value.toLowerCase()) {
            case "paid" -> PAID;
            case "failed" -> FAILED;
            case "pending" -> PENDING;
            case "refunded" -> REFUNDED;
            default -> throw new IllegalArgumentException("Unknown PaymentStatus: " + value);
        };
    }
}
