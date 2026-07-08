package com.marketmono.service.support;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportTicketRateLimitServiceTest {

    @Test
    void shouldLimitCreateAttemptsPerUserWithInMemoryFallback() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-03T12:00:00Z"));
        SupportTicketRateLimitService service = new SupportTicketRateLimitService(
            Optional.empty(),
            clock,
            2,
            10,
            Duration.ofMinutes(5),
            true
        );
        UUID userId = UUID.randomUUID();

        service.checkCreateAllowed(userId);
        service.checkCreateAllowed(userId);

        assertThatThrownBy(() -> service.checkCreateAllowed(userId))
            .isInstanceOf(SupportTicketRateLimitExceededException.class)
            .hasMessageContaining("Too many support ticket create requests");
    }

    @Test
    void shouldResetInMemoryCountersAfterWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-03T12:00:00Z"));
        SupportTicketRateLimitService service = new SupportTicketRateLimitService(
            Optional.empty(),
            clock,
            1,
            1,
            Duration.ofMinutes(5),
            true
        );
        UUID userId = UUID.randomUUID();

        service.checkReplyAllowed(userId);
        clock.advance(Duration.ofMinutes(6));

        service.checkReplyAllowed(userId);
    }

    @Test
    void shouldUseRedisWhenAvailableAndLimitCreateAttempts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-03T12:00:00Z"));
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);
        SupportTicketRateLimitService service = new SupportTicketRateLimitService(
            Optional.of(redisTemplate),
            clock,
            5,
            30,
            Duration.ofHours(1),
            false
        );
        UUID userId = UUID.randomUUID();

        service.checkCreateAllowed(userId);
        service.checkCreateAllowed(userId);
        service.checkCreateAllowed(userId);
        service.checkCreateAllowed(userId);
        service.checkCreateAllowed(userId);

        assertThatThrownBy(() -> service.checkCreateAllowed(userId))
            .isInstanceOf(SupportTicketRateLimitExceededException.class)
            .hasMessageContaining("Too many support ticket create requests");
        verify(redisTemplate).expire(anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    void shouldNotSilentlyFallBackWhenRedisFailsAndFallbackIsDisabled() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-03T12:00:00Z"));
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Valkey unavailable"));
        SupportTicketRateLimitService service = new SupportTicketRateLimitService(
            Optional.of(redisTemplate),
            clock,
            5,
            30,
            Duration.ofHours(1),
            false
        );

        assertThatThrownBy(() -> service.checkCreateAllowed(UUID.randomUUID()))
            .isInstanceOf(SupportTicketRateLimiterUnavailableException.class)
            .hasMessageContaining("Support ticket rate limiter is unavailable");
    }

    @Test
    void shouldRequireRedisWhenFallbackIsDisabledAndRedisIsMissing() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-03T12:00:00Z"));
        SupportTicketRateLimitService service = new SupportTicketRateLimitService(
            Optional.empty(),
            clock,
            5,
            30,
            Duration.ofHours(1),
            false
        );

        assertThatThrownBy(() -> service.checkReplyAllowed(UUID.randomUUID()))
            .isInstanceOf(SupportTicketRateLimiterUnavailableException.class)
            .hasMessageContaining("Support ticket rate limiter requires Redis or Valkey");
    }

    @Test
    void shouldDefaultToFiveCreatesAndThirtyRepliesPerHour() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-03T12:00:00Z"));
        SupportTicketRateLimitService service = new SupportTicketRateLimitService(Optional.empty(), clock, true);
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            service.checkCreateAllowed(userId);
        }
        assertThatThrownBy(() -> service.checkCreateAllowed(userId))
            .isInstanceOf(SupportTicketRateLimitExceededException.class);

        for (int i = 0; i < 30; i++) {
            service.checkReplyAllowed(userId);
        }
        assertThatThrownBy(() -> service.checkReplyAllowed(userId))
            .isInstanceOf(SupportTicketRateLimitExceededException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
