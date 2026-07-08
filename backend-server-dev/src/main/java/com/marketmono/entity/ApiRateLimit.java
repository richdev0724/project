package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity representing API rate limit tracking
 * Maps to api_rate_limits table for monitoring and enforcing request limits
 */
@Entity
@Table(name = "api_rate_limits", indexes = {
    @Index(name = "idx_rate_limits_key_window", columnList = "api_key_hash, window_start, window_end")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiRateLimit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;
    
    @Column(length = 200)
    private String endpoint;
    
    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private Integer requestCount = 0;
    
    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;
    
    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "limit_exceeded")
    @Builder.Default
    private Boolean limitExceeded = false;
    
    @Column(name = "daily_limit")
    private Integer dailyLimit;

    @Column(name = "minute_request_count")
    private Integer minuteRequestCount;

    @Column(name = "minute_limit_remaining")
    private Integer minuteLimitRemaining;

    @Column(name = "minute_limit")
    private Integer minuteLimit;

    @Column(name = "minute_reset_epoch")
    private Long minuteResetEpoch;
    
    @Column(name = "is_blocked")
    @Builder.Default
    private Boolean isBlocked = false;
    
    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;
    
    @Column(name = "http_status_code")
    private Integer httpStatusCode;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    /**
     * Increment request count
     */
    public void incrementRequestCount() {
        this.requestCount++;
        if (this.dailyLimit != null && this.requestCount >= this.dailyLimit) {
            this.limitExceeded = true;
        }
    }
    
    /**
     * Mark limit as exceeded (e.g., from 429 response)
     */
    public void markLimitExceeded() {
        this.limitExceeded = true;
    }
    
    /**
     * Check if limit is exceeded
     */
    public boolean isLimitExceeded() {
        return Boolean.TRUE.equals(limitExceeded) || 
               (dailyLimit != null && requestCount >= dailyLimit);
    }
    
    /**
     * Check if window is active (current time within window)
     */
    public boolean isWindowActive() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(windowStart) && !now.isAfter(windowEnd);
    }
}
