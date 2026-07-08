package com.marketmono.service.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class SupportTicketRateLimitService {

    private static final int DEFAULT_CREATE_LIMIT = 5;
    private static final int DEFAULT_REPLY_LIMIT = 30;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(1);
    private static final String CREATE_ACTION = "create";
    private static final String REPLY_ACTION = "reply";

    private final Optional<StringRedisTemplate> redisTemplate;
    private final Clock clock;
    private final int createLimit;
    private final int replyLimit;
    private final Duration window;
    private final boolean inMemoryFallbackEnabled;
    private final ConcurrentMap<String, WindowCounter> inMemoryCounters = new ConcurrentHashMap<>();

    @Autowired
    public SupportTicketRateLimitService(
            Optional<StringRedisTemplate> redisTemplate,
            Environment environment,
            @Value("${support.tickets.rate-limit.create-per-hour:5}") int createLimit,
            @Value("${support.tickets.rate-limit.reply-per-hour:30}") int replyLimit,
            @Value("${support.tickets.rate-limit.in-memory-fallback-enabled:false}")
            boolean explicitInMemoryFallbackEnabled) {
        this(
            redisTemplate,
            Clock.systemUTC(),
            createLimit,
            replyLimit,
            DEFAULT_WINDOW,
            isFallbackAllowed(environment, explicitInMemoryFallbackEnabled)
        );
    }

    SupportTicketRateLimitService(
            Optional<StringRedisTemplate> redisTemplate,
            Clock clock,
            boolean inMemoryFallbackEnabled) {
        this(
            redisTemplate,
            clock,
            DEFAULT_CREATE_LIMIT,
            DEFAULT_REPLY_LIMIT,
            DEFAULT_WINDOW,
            inMemoryFallbackEnabled
        );
    }

    SupportTicketRateLimitService(
            Optional<StringRedisTemplate> redisTemplate,
            Clock clock,
            int createLimit,
            int replyLimit,
            Duration window,
            boolean inMemoryFallbackEnabled) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.createLimit = createLimit;
        this.replyLimit = replyLimit;
        this.window = window;
        this.inMemoryFallbackEnabled = inMemoryFallbackEnabled;
    }

    public void checkCreateAllowed(UUID userId) {
        consume(userId, CREATE_ACTION, createLimit, "Too many support ticket create requests");
    }

    public void checkReplyAllowed(UUID userId) {
        consume(userId, REPLY_ACTION, replyLimit, "Too many support ticket reply requests");
    }

    private void consume(UUID userId, String action, int limit, String message) {
        if (limit <= 0) {
            return;
        }
        if (tryRedisConsume(userId, action, limit, message)) {
            return;
        }
        if (!inMemoryFallbackEnabled) {
            throw new SupportTicketRateLimiterUnavailableException(
                "Support ticket rate limiter requires Redis or Valkey outside local/test fallback mode"
            );
        }
        consumeInMemory(userId, action, limit, message);
    }

    private boolean tryRedisConsume(UUID userId, String action, int limit, String message) {
        if (redisTemplate.isEmpty()) {
            return false;
        }
        try {
            String key = redisKey(userId, action);
            Long count = redisTemplate.get().opsForValue().increment(key);
            if (count == null) {
                throw new IllegalStateException("Redis increment returned null");
            }
            if (count != null && count == 1L) {
                redisTemplate.get().expire(key, window);
            }
            if (count != null && count > limit) {
                throw new SupportTicketRateLimitExceededException(message, retryAfter());
            }
            return true;
        } catch (SupportTicketRateLimitExceededException exception) {
            throw exception;
        } catch (Exception exception) {
            if (!inMemoryFallbackEnabled) {
                throw new SupportTicketRateLimiterUnavailableException(
                    "Support ticket rate limiter is unavailable",
                    exception
                );
            }
            log.warn(
                "Support ticket Redis rate limit unavailable; using explicit in-memory fallback: {}",
                exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    private void consumeInMemory(UUID userId, String action, int limit, String message) {
        String key = action + ":" + userId;
        Instant now = clock.instant();
        WindowCounter counter = inMemoryCounters.compute(key, (_, existing) -> {
            if (existing == null || !existing.isActive(now)) {
                return new WindowCounter(now, 1);
            }
            existing.increment();
            return existing;
        });
        if (counter.count() > limit) {
            throw new SupportTicketRateLimitExceededException(message, retryAfter(counter.windowStartedAt(), now));
        }
    }

    private String redisKey(UUID userId, String action) {
        long windowId = clock.instant().getEpochSecond() / window.toSeconds();
        return "support:tickets:rate-limit:" + action + ":" + userId + ":" + windowId;
    }

    private static boolean isFallbackAllowed(Environment environment, boolean explicitInMemoryFallbackEnabled) {
        if (explicitInMemoryFallbackEnabled) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> "local".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile));
    }

    private Duration retryAfter() {
        long now = clock.instant().getEpochSecond();
        long windowSeconds = window.toSeconds();
        long nextWindow = ((now / windowSeconds) + 1) * windowSeconds;
        return Duration.ofSeconds(Math.max(1, nextWindow - now));
    }

    private Duration retryAfter(Instant windowStartedAt, Instant now) {
        return Duration.between(now, windowStartedAt.plus(window)).isNegative()
            ? Duration.ofSeconds(1)
            : Duration.between(now, windowStartedAt.plus(window));
    }

    private final class WindowCounter {
        private final Instant windowStartedAt;
        private int count;

        private WindowCounter(Instant windowStartedAt, int count) {
            this.windowStartedAt = windowStartedAt;
            this.count = count;
        }

        private boolean isActive(Instant now) {
            return now.isBefore(windowStartedAt.plus(window));
        }

        private void increment() {
            count++;
        }

        private int count() {
            return count;
        }

        private Instant windowStartedAt() {
            return windowStartedAt;
        }
    }
}
