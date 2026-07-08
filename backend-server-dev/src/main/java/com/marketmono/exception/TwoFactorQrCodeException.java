package com.marketmono.exception;

public class TwoFactorQrCodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TwoFactorQrCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
