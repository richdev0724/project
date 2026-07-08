package com.marketmono.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.marketmono.util.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP-based rate limiting service for registration attempts.
 * 
 * Strategy: Progressive cooldown
 * - 1-3 attempts in 10 min: Allow
 * - 4-5 attempts in 10 min: Allow (but frontend should show CAPTCHA challenge)
 * - 6+ attempts in 10 min: Block for 15 minutes
 * - 10+ attempts in 1 hour: Block for 1 hour
 */
@Service
@Slf4j
public class RegistrationRateLimitService {
    
    // Cache for tracking registration attempts per IP (10-minute window)
    private final Cache<String, AtomicInteger> shortWindowAttempts;
    
    // Cache for tracking registration attempts per IP (1-hour window)
    private final Cache<String, AtomicInteger> longWindowAttempts;
    
    // Cache for blocked IPs
    private final Cache<String, Long> blockedIps;
    
    @Value("${registration.rate-limit.short-window-max:5}")
    private int shortWindowMax;
    
    @Value("${registration.rate-limit.long-window-max:10}")
    private int longWindowMax;
    
    @Value("${registration.rate-limit.short-block-minutes:15}")
    private int shortBlockMinutes;
    
    @Value("${registration.rate-limit.long-block-minutes:60}")
    private int longBlockMinutes;
    
    public RegistrationRateLimitService() {
        // 10-minute window for short-term tracking
        this.shortWindowAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10000)
            .build();
        
        // 1-hour window for long-term tracking
        this.longWindowAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(10000)
            .build();
        
        // Block cache with max 1-hour expiry
        this.blockedIps = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(10000)
            .build();
    }
    
    /**
     * Check if the IP is allowed to attempt registration.
     * 
     * @param request HTTP request to extract IP from
     * @throws RegistrationRateLimitException if rate limit exceeded
     */
    public void checkRateLimit(HttpServletRequest request) {
        String clientIp = HttpUtils.getClientIpWithRealIpFallback(request);
        
        // Check if IP is blocked
        Long blockedUntil = blockedIps.getIfPresent(clientIp);
        if (blockedUntil != null) {
            long remainingSeconds = (blockedUntil - System.currentTimeMillis()) / 1000;
            if (remainingSeconds > 0) {
                log.warn("Blocked IP {} attempted registration. {} seconds remaining", maskIp(clientIp), remainingSeconds);
                throw new RegistrationRateLimitException(
                    String.format("Too many registration attempts. Please try again in %d minutes.", remainingSeconds / 60 + 1)
                );
            }
            // Block expired, remove it
            blockedIps.invalidate(clientIp);
        }
        
        log.debug("Registration rate limit check passed for IP: {}", maskIp(clientIp));
    }
    
    /**
     * Record a registration attempt from the given IP.
     * Should be called after a registration attempt (success or failure).
     * 
     * @param request HTTP request to extract IP from
     */
    public void recordAttempt(HttpServletRequest request) {
        String clientIp = HttpUtils.getClientIpWithRealIpFallback(request);
        
        // Increment short window counter
        AtomicInteger shortCount = shortWindowAttempts.get(clientIp, _ -> new AtomicInteger(0));
        int shortAttempts = shortCount.incrementAndGet();
        
        // Increment long window counter
        AtomicInteger longCount = longWindowAttempts.get(clientIp, _ -> new AtomicInteger(0));
        int longAttempts = longCount.incrementAndGet();
        
        log.debug("Registration attempt recorded for IP {}: short={}, long={}", 
            maskIp(clientIp), shortAttempts, longAttempts);
        
        // Check if we need to block
        if (longAttempts >= longWindowMax) {
            // Long-term block (1 hour)
            long blockUntil = System.currentTimeMillis() + (longBlockMinutes * 60 * 1000L);
            blockedIps.put(clientIp, blockUntil);
            log.warn("IP {} blocked for {} minutes due to excessive registration attempts (long window)", 
                maskIp(clientIp), longBlockMinutes);
        } else if (shortAttempts >= shortWindowMax) {
            // Short-term block (15 minutes)
            long blockUntil = System.currentTimeMillis() + (shortBlockMinutes * 60 * 1000L);
            blockedIps.put(clientIp, blockUntil);
            log.warn("IP {} blocked for {} minutes due to excessive registration attempts (short window)", 
                maskIp(clientIp), shortBlockMinutes);
        }
    }
    
    /**
     * Check if CAPTCHA should be shown (for progressive challenge).
     * Returns true if the IP has made 3+ attempts in the short window.
     * 
     * @param request HTTP request to extract IP from
     * @return true if CAPTCHA challenge should be enforced
     */
    public boolean shouldRequireCaptcha(HttpServletRequest request) {
        String clientIp = HttpUtils.getClientIpWithRealIpFallback(request);
        AtomicInteger count = shortWindowAttempts.getIfPresent(clientIp);
        return count != null && count.get() >= 3;
    }
    
    /**
     * Mask IP for logging (privacy).
     * Example: "192.168.1.100" -> "192.168.1.***"
     */
    private String maskIp(String ip) {
        if (ip == null) return "unknown";
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".***";
        }
        return ip;
    }
    
    /**
     * Custom exception for registration rate limit.
     */
    public static class RegistrationRateLimitException extends RuntimeException {
        public RegistrationRateLimitException(String message) {
            super(message);
        }
    }
}
