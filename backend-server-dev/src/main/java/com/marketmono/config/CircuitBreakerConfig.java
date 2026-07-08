package com.marketmono.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Circuit Breaker configuration for API rate limiting coordination
 * 
 * When rate limit (429) is detected:
 * - Circuit opens immediately
 * - All subsequent requests fail fast without hitting API
 * - After wait duration, circuit enters half-open state
 * - Tests with single request before fully closing
 */
@Configuration
public class CircuitBreakerConfig {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = 
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                // Circuit opens only after high failure rate (80% failures)
                // More lenient than before (was 50%)
                .failureRateThreshold(80)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(15))
                
                // Larger window to average out transient failures
                // Tracks last 50 calls instead of 10
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(50)
                
                // Requires more calls before circuit can trip (10 instead of 5)
                // Prevents premature opening on startup or low traffic
                .minimumNumberOfCalls(10)
                
                // Shorter wait time - recovers faster (30s instead of 60s)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                
                // Test with 3 requests in half-open state (was 5)
                .permittedNumberOfCallsInHalfOpenState(3)
                
                // Automatically transition from open to half-open
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                
                // Only record 429 (rate limit) as failures
                // Ignores other errors to prevent circuit opening unnecessarily
                .recordException(throwable -> {
                    if (throwable instanceof WebClientResponseException e) {
                        return e.getStatusCode().value() == 429;
                    }
                    return false;
                })
                
                // Ignore specific exceptions that shouldn't trip the circuit
                .ignoreException(throwable -> {
                    if (throwable instanceof WebClientResponseException e) {
                        int status = e.getStatusCode().value();
                        // Ignore client errors (400, 404, 422) - not circuit-worthy
                        return status >= 400 && status < 500 && status != 429;
                    }
                    return false;
                })
                
                .build();
        
        return CircuitBreakerRegistry.of(config);
    }
    
    @Bean
    public CircuitBreaker unusualWhalesCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("market-data-api");
        
        // Log circuit breaker state transitions
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                String from = event.getStateTransition().getFromState().name();
                String to = event.getStateTransition().getToState().name();
                org.slf4j.LoggerFactory.getLogger(CircuitBreakerConfig.class)
                    .info("Circuit breaker {} -> {}", from, to);
            })
            .onFailureRateExceeded(event -> 
                org.slf4j.LoggerFactory.getLogger(CircuitBreakerConfig.class)
                    .warn("Failure rate {}% exceeded threshold", event.getFailureRate())
            );
        
        return circuitBreaker;
    }
}
