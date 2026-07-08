package com.marketmono.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class FlowFilterBusyException extends RuntimeException {

    private final transient Map<String, Object> attribution;
    private final int retryAfterSeconds;

    public FlowFilterBusyException(
        String message,
        Map<String, Object> attribution,
        int retryAfterSeconds
    ) {
        super(message);
        this.attribution = new LinkedHashMap<>(attribution);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Map<String, Object> attribution() {
        return attribution;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
