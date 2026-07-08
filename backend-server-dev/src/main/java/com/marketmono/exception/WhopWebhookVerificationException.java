package com.marketmono.exception;

/**
 * Raised when a Whop webhook cannot be authenticated with Standard Webhooks headers.
 */
public class WhopWebhookVerificationException extends RuntimeException {

    public WhopWebhookVerificationException(String message) {
        super(message);
    }

    public WhopWebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
