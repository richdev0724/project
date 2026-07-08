package com.marketmono.exception;

public class MarketDataProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MarketDataProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
