package com.marketmono.exception;

public class OhlcRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OhlcRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
