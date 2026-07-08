package com.marketmono.service.support;

public class SupportTicketRateLimiterUnavailableException extends RuntimeException {

    public SupportTicketRateLimiterUnavailableException(String message) {
        super(message);
    }

    public SupportTicketRateLimiterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
