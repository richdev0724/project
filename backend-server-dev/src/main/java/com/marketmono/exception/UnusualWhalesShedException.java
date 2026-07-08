package com.marketmono.exception;

public class UnusualWhalesShedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String operationName;
    private final String reason;
    private final int retryAfterSeconds;

    public UnusualWhalesShedException(String operationName, String reason, int retryAfterSeconds) {
        super("Market data request shed for " + operationName + " (" + reason + ")");
        this.operationName = operationName;
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String operationName() {
        return operationName;
    }

    public String reason() {
        return reason;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
