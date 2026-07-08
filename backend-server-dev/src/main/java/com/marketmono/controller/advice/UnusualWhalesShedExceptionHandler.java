package com.marketmono.controller.advice;

import com.marketmono.exception.UnusualWhalesShedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class UnusualWhalesShedExceptionHandler {

    @ExceptionHandler(UnusualWhalesShedException.class)
    public ResponseEntity<Map<String, Object>> handleUnusualWhalesShed(UnusualWhalesShedException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
            .body(Map.of(
                "code", "MARKET_DATA_THROTTLED",
                "message", "Market data is temporarily unavailable. Retry shortly.",
                "operation", exception.operationName(),
                "reason", exception.reason(),
                "retryAfterSeconds", exception.retryAfterSeconds()
            ));
    }
}
