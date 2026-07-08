package com.marketmono.exception;

public class WhopCheckoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WhopCheckoutException(String message) {
        super(message);
    }

    public WhopCheckoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
