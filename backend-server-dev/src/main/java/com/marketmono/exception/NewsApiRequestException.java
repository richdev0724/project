package com.marketmono.exception;

public class NewsApiRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NewsApiRequestException(String message) {
        super(message);
    }

    public NewsApiRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
