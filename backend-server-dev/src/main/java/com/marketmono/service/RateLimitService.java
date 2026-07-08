package com.marketmono.service;

import com.marketmono.entity.ApiRateLimit;
import com.marketmono.exception.RateLimitExceededException;
import com.marketmono.repository.ApiRateLimitRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for tracking and enforcing API rate limits
 * Uses api_rate_limits table to prevent requests when daily limit is hit
 * 
 * Upstream market data API limits:
 * - Daily limit: 15,000 requests (configurable)
 * - Resets at 8 PM EST / 5 PM PST (post-market close)
 * - Only 200 status codes count toward limit
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    public static final String MARKET_DATA_HTTP_422_ACCESS_BLOCKED = "market_data_http_422_access_blocked";
    public static final String MARKET_DATA_HTTP_422_VALIDATION_ERROR = "market_data_http_422_validation_error";
    
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");
    private static final int RESET_HOUR = 20; // 8 PM EST
    private static final String MARKET_DATA_BUDGET_DAILY_USAGE_METRIC =
        "marketmono.market_data.budget.daily.usage";
    private static final String MARKET_DATA_BUDGET_DAILY_LIMIT_METRIC =
        "marketmono.market_data.budget.daily.limit";
    private static final String MARKET_DATA_BUDGET_DAILY_REMAINING_METRIC =
        "marketmono.market_data.budget.daily.remaining";
    private static final String MARKET_DATA_BUDGET_DAILY_PROJECTED_USAGE_METRIC =
        "marketmono.market_data.budget.daily.projected.usage";
    
    private final ApiRateLimitRepository rateLimitRepository;
    private final AtomicInteger observedDailyUsage = new AtomicInteger();
    private final AtomicInteger observedDailyLimit = new AtomicInteger();
    private final AtomicInteger observedDailyRemaining = new AtomicInteger();
    private final AtomicInteger observedDailyProjectedUsage = new AtomicInteger();

    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    
    @Value("${unusual-whales.api.api-key}") 
    private String apiKey;
    
    @Value("${unusual-whales.api.rate-limit.daily-limit:100000}")
    private int configuredDailyLimit;

    @PostConstruct
    void registerDailyQuotaGauges() {
        if (meterRegistry == null) {
            return;
        }

        observedDailyLimit.set(configuredDailyLimit);
        observedDailyRemaining.set(configuredDailyLimit);

        Gauge.builder(MARKET_DATA_BUDGET_DAILY_USAGE_METRIC, observedDailyUsage, AtomicInteger::get)
            .description("Observed daily market data requests used from upstream rate-limit headers")
            .register(meterRegistry);
        Gauge.builder(MARKET_DATA_BUDGET_DAILY_LIMIT_METRIC, observedDailyLimit, AtomicInteger::get)
            .description("Observed or configured market data daily request limit")
            .register(meterRegistry);
        Gauge.builder(MARKET_DATA_BUDGET_DAILY_REMAINING_METRIC, observedDailyRemaining, AtomicInteger::get)
            .description("Observed market data daily requests remaining")
            .register(meterRegistry);
        Gauge.builder(
                MARKET_DATA_BUDGET_DAILY_PROJECTED_USAGE_METRIC,
                observedDailyProjectedUsage,
                AtomicInteger::get
            )
            .description("Projected market data daily requests for the current reset window based on observed usage")
            .register(meterRegistry);
    }
    
    private String getApiKeyHash() {
        return hashApiKey(apiKey);
    }
    
    /**
     * Check if rate limit allows making a request
     * Throws RateLimitExceededException if limit is exceeded
     * 
     * @throws RateLimitExceededException if limit is exceeded
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkRateLimit() {
        LocalDateTime now = LocalDateTime.now();
        ApiRateLimit rateLimit = getOrCreateRateLimit(now);
        recordDailyQuotaMetrics(rateLimit);

        // Check if endpoint is permanently blocked (422 errors)
        if (Boolean.TRUE.equals(rateLimit.getIsBlocked())) {
            String reason = rateLimit.getBlockedReason() != null ? rateLimit.getBlockedReason() : "Access denied";
            Integer statusCode = rateLimit.getHttpStatusCode();
            log.warn("Endpoint blocked: {} (HTTP {})", reason, statusCode);
            throw new RateLimitExceededException(
                String.format("Endpoint blocked: %s (HTTP %s)", reason, statusCode)
            );
        }

        int effectiveDailyLimit = resolveDailyLimit(rateLimit);
        Integer currentCount = rateLimit.getRequestCount();

        boolean dailyExceeded = Boolean.TRUE.equals(rateLimit.getLimitExceeded())
            || (effectiveDailyLimit > 0 && currentCount != null && currentCount >= effectiveDailyLimit);

        if (dailyExceeded) {
            String resetTime = formatResetTime(rateLimit.getWindowEnd());
            log.warn("Daily rate limit exceeded: {}/{} requests used. Resets at {}",
                currentCount, effectiveDailyLimit, resetTime);

            throw new RateLimitExceededException(
                currentCount != null ? currentCount : -1,
                effectiveDailyLimit,
                resetTime
            );
        }

        // Check minute rate limit only if data is fresh (not stale)
        if (rateLimit.getMinuteLimitRemaining() != null && rateLimit.getMinuteLimitRemaining() <= 0) {
            Long minuteResetEpoch = rateLimit.getMinuteResetEpoch();
            long currentEpoch = System.currentTimeMillis() / 1000;
            
            // If reset epoch is in the past, the data is stale - ignore it
            if (minuteResetEpoch != null && minuteResetEpoch < currentEpoch) {
                log.debug("Minute rate limit data is stale (reset was at epoch {}), ignoring", minuteResetEpoch);
                // Clear stale minute rate limit data
                rateLimit.setMinuteRequestCount(null);
                rateLimit.setMinuteLimitRemaining(null);
                rateLimit.setMinuteLimit(null);
                rateLimit.setMinuteResetEpoch(null);
                rateLimitRepository.save(rateLimit);
            } else {
                // Data is fresh - enforce the minute limit
                String resetDescriptor = describeMinuteReset(minuteResetEpoch);
                int minuteUsed = rateLimit.getMinuteRequestCount() != null ? rateLimit.getMinuteRequestCount() : -1;
                Integer minuteLimit = rateLimit.getMinuteLimit();
                log.warn("Minute rate limit exceeded: {}/{} requests used. Reset header: {}",
                    minuteUsed, minuteLimit, resetDescriptor);

                throw new RateLimitExceededException(String.format(
                    "API minute rate limit exceeded: %d/%s requests used. Header reset value: %s",
                    minuteUsed,
                    minuteLimit != null ? minuteLimit : "unknown",
                    resetDescriptor
                ));
            }
        }

        log.debug("Rate limit check passed: daily {}/{} | minute remaining {}",
            currentCount,
            effectiveDailyLimit,
            rateLimit.getMinuteLimitRemaining());
    }

    /**
     * Record a successful API request (200 status code)
     * Applies observed headers when available to keep counters in sync
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulRequest(HttpHeaders headers) {
        LocalDateTime now = LocalDateTime.now();
        ApiRateLimit rateLimit = getOrCreateRateLimit(now);

        RateLimitSnapshot snapshot = parseRateLimitHeaders(headers);
        if (snapshot != null) {
            applySnapshot(rateLimit, snapshot, false);
        } else {
            rateLimit.incrementRequestCount();
        }

        rateLimitRepository.save(rateLimit);
        recordDailyQuotaMetrics(rateLimit);

        log.debug("Recorded successful request: daily {}/{} | minute count {} remaining {}",
            rateLimit.getRequestCount(),
            resolveDailyLimit(rateLimit),
            rateLimit.getMinuteRequestCount(),
            rateLimit.getMinuteLimitRemaining());

        if (Boolean.TRUE.equals(rateLimit.getLimitExceeded())) {
            log.warn("Rate limit threshold reached: {}/{} requests",
                rateLimit.getRequestCount(), resolveDailyLimit(rateLimit));
        }
    }

    /**
     * Record a rate limit hit from 429 response
     * Marks the limit as exceeded for the remainder of the window
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRateLimitHit(HttpHeaders headers) {
        LocalDateTime now = LocalDateTime.now();
        ApiRateLimit rateLimit = getOrCreateRateLimit(now);

        RateLimitSnapshot snapshot = parseRateLimitHeaders(headers);
        applySnapshot(rateLimit, snapshot, true);
        rateLimit.markLimitExceeded();

        if (rateLimit.getMinuteLimitRemaining() != null && rateLimit.getMinuteLimitRemaining() > 0) {
            rateLimit.setMinuteLimitRemaining(0);
        }

        rateLimitRepository.save(rateLimit);
        recordDailyQuotaMetrics(rateLimit);

        String resetTime = formatResetTime(rateLimit.getWindowEnd());
        log.error("API rate limit hit (429 response). No more requests until {}", resetTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRateLimitHit() {
        recordRateLimitHit(null);
    }
    
    /**
     * Record when an endpoint returns 422 status code
     * 
     * IMPORTANT: Only permanently blocks endpoints for access/permission errors (enterprise-only, missing access)
     * Does NOT block for validation errors (invalid dates, missing parameters) - those are client errors that can be fixed
     * 
     * @param statusCode HTTP status code (should be 422)
     * @param reason Safe reason category for the upstream 422 response
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlockedEndpoint(int statusCode, String reason) {
        boolean isPermanentBlock = isPermanentAccessBlockReason(reason);
        
        // Don't permanently block for validation errors (invalid dates, missing params, etc.)
        if (!isPermanentBlock) {
            log.warn("Endpoint returned 422 validation error (not permanently blocked): {}", safe422ReasonForLogging(reason));
            return; // Don't block the endpoint
        }
        
        // For enterprise-only or permission errors, mark as permanently blocked
        LocalDateTime now = LocalDateTime.now();
        ApiRateLimit rateLimit = getOrCreateRateLimit(now);
        
        rateLimit.setIsBlocked(true);
        rateLimit.setHttpStatusCode(statusCode);
        rateLimit.setBlockedReason(MARKET_DATA_HTTP_422_ACCESS_BLOCKED);
        
        rateLimitRepository.save(rateLimit);
        recordDailyQuotaMetrics(rateLimit);
        
        log.error(
            "Endpoint permanently blocked (access/permission denied): HTTP {} - {}",
            statusCode,
            MARKET_DATA_HTTP_422_ACCESS_BLOCKED
        );
    }

    private boolean isPermanentAccessBlockReason(String reason) {
        if (MARKET_DATA_HTTP_422_ACCESS_BLOCKED.equals(reason)) {
            return true;
        }
        if (reason == null || reason.isBlank() || MARKET_DATA_HTTP_422_VALIDATION_ERROR.equals(reason)) {
            return false;
        }
        String normalizedReason = reason.toLowerCase(Locale.ROOT);
        return normalizedReason.contains("enterprise only endpoint")
            || normalizedReason.contains("enterprise-only endpoint")
            || normalizedReason.contains("missing access")
            || normalizedReason.contains("access denied")
            || normalizedReason.contains("not authorized");
    }

    private String safe422ReasonForLogging(String reason) {
        return MARKET_DATA_HTTP_422_VALIDATION_ERROR.equals(reason)
            ? MARKET_DATA_HTTP_422_VALIDATION_ERROR
            : "market_data_http_422_non_blocking";
    }
    
    /**
     * Get current rate limit status
     * 
     * @return current rate limit record
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApiRateLimit getCurrentRateLimitStatus() {
        LocalDateTime now = LocalDateTime.now();
        ApiRateLimit rateLimit = getOrCreateRateLimit(now);
        recordDailyQuotaMetrics(rateLimit);
        return rateLimit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int getDailyProjectedUsage() {
        LocalDateTime now = LocalDateTime.now();
        ApiRateLimit rateLimit = getOrCreateRateLimit(now);
        int usage = rateLimit.getRequestCount() != null ? Math.max(rateLimit.getRequestCount(), 0) : 0;
        int projectedUsage = projectedDailyUsage(rateLimit, usage);
        recordDailyQuotaMetrics(rateLimit);
        return projectedUsage;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isDailyProjectionAtOrAbove(int threshold) {
        return threshold > 0 && getDailyProjectedUsage() >= threshold;
    }
    
    /**
     * Get or create rate limit record for current window
     * Checks if we've entered a new rate limit period and clears exceeded flag if so
     */
    private ApiRateLimit getOrCreateRateLimit(LocalDateTime now) {
        Optional<ApiRateLimit> existingLimit = rateLimitRepository.findActiveRateLimit(getApiKeyHash(), now);
        
        if (existingLimit.isPresent()) {
            ApiRateLimit rateLimit = existingLimit.get();
            
            // Check if we're in a new rate limit period (after the last window end)
            // This handles cases where limitExceeded was set but we've moved into a new period
            ZonedDateTime nowEst = now.atZone(EST_ZONE);
            ZonedDateTime currentWindowEnd = nowEst.toLocalDate().atTime(RESET_HOUR, 0).atZone(EST_ZONE);
            if (nowEst.isAfter(currentWindowEnd)) {
                currentWindowEnd = currentWindowEnd.plusDays(1);
            }
            ZonedDateTime currentWindowStart = currentWindowEnd.minusDays(1);
            
            LocalDateTime expectedWindowStart = currentWindowStart.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            
            // If this rate limit was from a previous period, create a new one
            if (rateLimit.getWindowStart().isBefore(expectedWindowStart)) {
                log.info("Old rate limit window found, creating new window for current period");
                return createNewRateLimitWindow(now);
            }
            
            return rateLimit;
        }
        
        return createNewRateLimitWindow(now);
    }
    
    /**
     * Manually reset the rate limit (for testing or if incorrectly flagged)
     * Clears both daily and minute rate limit exceeded flags
     */
    @Transactional
    public void resetRateLimit() {
        LocalDateTime now = LocalDateTime.now();
        Optional<ApiRateLimit> existing = rateLimitRepository.findActiveRateLimit(getApiKeyHash(), now);
        
        if (existing.isPresent()) {
            ApiRateLimit rateLimit = existing.get();
            // Clear daily limit flags
            rateLimit.setLimitExceeded(false);
            rateLimit.setRequestCount(0);
            // Clear minute limit fields (stale data)
            rateLimit.setMinuteRequestCount(null);
            rateLimit.setMinuteLimitRemaining(null);
            rateLimit.setMinuteLimit(null);
            rateLimit.setMinuteResetEpoch(null);
            rateLimitRepository.save(rateLimit);
            recordDailyQuotaMetrics(rateLimit);
            log.info("Rate limit manually reset (daily and minute): window {} to {}",
                formatResetTime(rateLimit.getWindowStart()),
                formatResetTime(rateLimit.getWindowEnd()));
        }
    }
    
    /**
     * Create new rate limit window
     * Window: previous day 8 PM EST to today 8 PM EST (24-hour window)
     */
    private ApiRateLimit createNewRateLimitWindow(LocalDateTime now) {
        ZonedDateTime nowEst = now.atZone(EST_ZONE);
        
        // Window end: today at 8 PM EST (or tomorrow if before 8 PM today)
        ZonedDateTime windowEndEst = nowEst.toLocalDate().atTime(RESET_HOUR, 0).atZone(EST_ZONE);
        if (nowEst.isBefore(windowEndEst)) {
            // If before 8 PM today, window ends today at 8 PM
            // Window starts yesterday at 8 PM
        } else {
            // If after 8 PM today, window ends tomorrow at 8 PM
            // Window starts today at 8 PM
            windowEndEst = windowEndEst.plusDays(1);
        }
        
        // Window start is always 24 hours before window end
        ZonedDateTime windowStartEst = windowEndEst.minusDays(1);
        
        LocalDateTime windowStart = windowStartEst.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime windowEnd = windowEndEst.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        
        ApiRateLimit rateLimit = ApiRateLimit.builder()
            .apiKeyHash(getApiKeyHash())
            .endpoint(null)
            .windowStart(windowStart)
            .windowEnd(windowEnd)
            .dailyLimit(configuredDailyLimit)
            .limitExceeded(false)  // Explicitly set to false for new windows
            .build();
        rateLimitRepository.save(rateLimit);
        recordDailyQuotaMetrics(rateLimit);
        
        if (log.isInfoEnabled()) {
            log.info("Created new rate limit window: {} to {}",
                formatResetTime(windowStart), formatResetTime(windowEnd));
        }
        
        return rateLimit;
    }

    private int resolveDailyLimit(ApiRateLimit rateLimit) {
        Integer storedLimit = rateLimit.getDailyLimit();
        return storedLimit != null && storedLimit > 0 ? storedLimit : configuredDailyLimit;
    }

    private void recordDailyQuotaMetrics(ApiRateLimit rateLimit) {
        if (rateLimit == null) {
            return;
        }

        int usage = rateLimit.getRequestCount() != null ? Math.max(rateLimit.getRequestCount(), 0) : 0;
        int limit = Math.max(resolveDailyLimit(rateLimit), 0);
        observedDailyUsage.set(usage);
        observedDailyLimit.set(limit);
        observedDailyRemaining.set(Math.max(limit - usage, 0));
        observedDailyProjectedUsage.set(projectedDailyUsage(rateLimit, usage));
    }

    private int projectedDailyUsage(ApiRateLimit rateLimit, int usage) {
        if (usage <= 0 || rateLimit.getWindowStart() == null || rateLimit.getWindowEnd() == null) {
            return Math.max(usage, 0);
        }

        long totalSeconds = Math.max(1L, Duration.between(rateLimit.getWindowStart(), rateLimit.getWindowEnd()).toSeconds());
        long elapsedSeconds = Duration.between(rateLimit.getWindowStart(), LocalDateTime.now()).toSeconds();
        if (elapsedSeconds <= 0) {
            return usage;
        }

        double projected = Math.ceil(usage * (totalSeconds / (double) elapsedSeconds));
        if (projected >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(usage, (int) projected);
    }

    private RateLimitSnapshot parseRateLimitHeaders(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }

        Integer dailyCount = parseIntegerHeader(headers, "x-uw-daily-req-count");
        Integer dailyLimitHeader = parseIntegerHeader(headers, "x-uw-token-req-limit");
        Integer minuteCount = parseIntegerHeader(headers, "x-uw-minute-req-counter");
        Integer minuteRemaining = parseIntegerHeader(headers, "x-uw-req-per-minute-remaining");
        Long minuteReset = parseLongHeader(headers, "x-uw-req-per-minute-reset");

        Integer minuteLimit = null;
        if (minuteCount != null && minuteRemaining != null) {
            minuteLimit = minuteCount + minuteRemaining;
        }

        if (dailyCount == null && dailyLimitHeader == null && minuteCount == null
            && minuteRemaining == null && minuteReset == null) {
            return null;
        }

        return new RateLimitSnapshot(dailyCount, dailyLimitHeader, minuteCount, minuteRemaining, minuteLimit, minuteReset);
    }

    private void applySnapshot(ApiRateLimit rateLimit, RateLimitSnapshot snapshot, boolean forceLimitExceeded) {
        if (snapshot == null) {
            return;
        }

        applySnapshotValues(rateLimit, snapshot);

        normalizeMinuteRemaining(rateLimit);

        if (forceLimitExceeded) {
            rateLimit.setLimitExceeded(true);
            return;
        }

        updateLimitExceededFlag(rateLimit, snapshot);
    }

    private void applySnapshotValues(ApiRateLimit rateLimit, RateLimitSnapshot snapshot) {
        if (snapshot.dailyLimit() != null && snapshot.dailyLimit() > 0) {
            rateLimit.setDailyLimit(snapshot.dailyLimit());
        }
        if (snapshot.dailyCount() != null) {
            rateLimit.setRequestCount(snapshot.dailyCount());
        }
        if (snapshot.minuteCount() != null) {
            rateLimit.setMinuteRequestCount(snapshot.minuteCount());
        }
        if (snapshot.minuteRemaining() != null) {
            rateLimit.setMinuteLimitRemaining(snapshot.minuteRemaining());
        }
        if (snapshot.minuteLimit() != null) {
            rateLimit.setMinuteLimit(snapshot.minuteLimit());
        }
        if (snapshot.minuteResetRaw() != null) {
            rateLimit.setMinuteResetEpoch(snapshot.minuteResetRaw());
        }
    }

    private void normalizeMinuteRemaining(ApiRateLimit rateLimit) {
        if (rateLimit.getMinuteLimitRemaining() != null && rateLimit.getMinuteLimitRemaining() < 0) {
            rateLimit.setMinuteLimitRemaining(0);
        }
    }

    private void updateLimitExceededFlag(ApiRateLimit rateLimit, RateLimitSnapshot snapshot) {
        Integer dailyCount = snapshot.dailyCount();
        if (dailyCount == null) {
            return;
        }
        int limit = resolveDailyLimit(rateLimit);
        if (limit > 0 && dailyCount >= limit) {
            rateLimit.setLimitExceeded(true);
            return;
        }
        if (Boolean.TRUE.equals(rateLimit.getLimitExceeded()) && limit > 0 && dailyCount < limit) {
            rateLimit.setLimitExceeded(false);
        }
    }

    private Integer parseIntegerHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            log.debug("Unable to parse integer header {}='{}'", name, value);
            return null;
        }
    }

    private Long parseLongHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException _) {
            log.debug("Unable to parse long header {}='{}'", name, value);
            return null;
        }
    }

    private String describeMinuteReset(Long rawValue) {
        return rawValue != null ? rawValue.toString() : "unknown";
    }
    
    /**
     * Hash API key for storage (security)
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Format reset time for logging/error messages
     */
    private String formatResetTime(LocalDateTime time) {
        ZonedDateTime estTime = time.atZone(ZoneId.systemDefault()).withZoneSameInstant(EST_ZONE);
        return estTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a z"));
    }
    
    /**
     * Clean up expired rate limit records (call from scheduler)
     */
    @Transactional
    public void cleanupExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        rateLimitRepository.deleteByWindowEndBefore(cutoff);
        log.info("Cleaned up rate limit records older than {}", cutoff);
    }

    private record RateLimitSnapshot(
        Integer dailyCount,
        Integer dailyLimit,
        Integer minuteCount,
        Integer minuteRemaining,
        Integer minuteLimit,
        Long minuteResetRaw
    ) {}
}
