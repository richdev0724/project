package com.marketmono.service.support;

import java.time.Duration;

public class SupportTicketRateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public SupportTicketRateLimitExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
