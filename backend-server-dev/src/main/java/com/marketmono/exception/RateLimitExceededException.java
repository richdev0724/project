package com.marketmono.exception;

/**
 * Exception thrown when API rate limit is exceeded
 * Indicates that no more requests should be made until the limit resets
 */
public class RateLimitExceededException extends RuntimeException {
    
    private final int currentCount;
    private final int dailyLimit;
    private final String resetTime;
    
    public RateLimitExceededException(int currentCount, int dailyLimit, String resetTime) {
        super(String.format(
            "API rate limit exceeded: %d/%d requests used. Limit resets at %s",
            currentCount, dailyLimit, resetTime
        ));
        this.currentCount = currentCount;
        this.dailyLimit = dailyLimit;
        this.resetTime = resetTime;
    }
    
    public RateLimitExceededException(String message) {
        super(message);
        this.currentCount = -1;
        this.dailyLimit = -1;
        this.resetTime = "unknown";
    }
    
    public int getCurrentCount() {
        return currentCount;
    }
    
    public int getDailyLimit() {
        return dailyLimit;
    }
    
    public String getResetTime() {
        return resetTime;
    }
}
