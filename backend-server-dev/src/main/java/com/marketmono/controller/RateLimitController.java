package com.marketmono.controller;

import com.marketmono.entity.ApiRateLimit;
import com.marketmono.service.RateLimitService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for monitoring API rate limit status
 */
@RestController
@RequestMapping("/api/v1/rate-limit")
public class RateLimitController {
    
    private final RateLimitService rateLimitService;
    private final CircuitBreaker circuitBreaker;
    
    public RateLimitController(RateLimitService rateLimitService, CircuitBreaker unusualWhalesCircuitBreaker) {
        this.rateLimitService = rateLimitService;
        this.circuitBreaker = unusualWhalesCircuitBreaker;
    }
    
    /**
     * Get current rate limit status
     * 
     * @return Rate limit information including current count, limit, and reset time
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRateLimitStatus() {
        ApiRateLimit rateLimit = rateLimitService.getCurrentRateLimitStatus();
        
        Map<String, Object> status = new HashMap<>();
        Integer requestCount = rateLimit.getRequestCount();
        Integer dailyLimit = rateLimit.getDailyLimit();
        status.put("requestCount", requestCount);
        status.put("dailyLimit", dailyLimit);
        status.put("limitExceeded", rateLimit.isLimitExceeded());
        status.put("windowStart", rateLimit.getWindowStart().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        status.put("windowEnd", rateLimit.getWindowEnd().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (dailyLimit != null && dailyLimit > 0 && requestCount != null) {
            status.put("remainingRequests", Math.max(dailyLimit - requestCount, 0));
            status.put("usagePercentage", String.format("%.2f%%",
                (requestCount * 100.0) / dailyLimit));
        } else {
            status.put("remainingRequests", null);
            status.put("usagePercentage", null);
        }
        status.put("minuteRequestCount", rateLimit.getMinuteRequestCount());
        status.put("minuteLimit", rateLimit.getMinuteLimit());
        status.put("minuteRemaining", rateLimit.getMinuteLimitRemaining());
        status.put("minuteResetValue", rateLimit.getMinuteResetEpoch());
        boolean minuteExceeded = rateLimit.getMinuteLimitRemaining() != null
            && rateLimit.getMinuteLimitRemaining() <= 0;
        status.put("minuteLimitExceeded", minuteExceeded);
        
        // Blocked endpoint status
        status.put("isBlocked", rateLimit.getIsBlocked());
        status.put("blockedReason", rateLimit.getBlockedReason());
        status.put("httpStatusCode", rateLimit.getHttpStatusCode());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Manually reset the rate limit
     * Clears limitExceeded flag and resets request count
     * Useful for clearing false positives or testing
     * 
     * @return Success message
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetRateLimit() {
        rateLimitService.resetRateLimit();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rate limit has been reset successfully");
        response.put("status", "success");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get circuit breaker status
     * 
     * @return Circuit breaker state and metrics
     */
    @GetMapping("/circuit-breaker/status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("state", circuitBreaker.getState().toString());
        status.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
        status.put("numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls());
        status.put("numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
        status.put("numberOfBufferedCalls", circuitBreaker.getMetrics().getNumberOfBufferedCalls());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Manually reset the circuit breaker
     * Forces circuit breaker to CLOSED state
     * 
     * @return Success message
     */
    @PostMapping("/circuit-breaker/reset")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker() {
        circuitBreaker.reset();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Circuit breaker has been reset to CLOSED state");
        response.put("status", "success");
        response.put("currentState", circuitBreaker.getState().toString());
        
        return ResponseEntity.ok(response);
    }
}
