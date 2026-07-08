package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecentRedisBufferService {

    private static final Duration DEFAULT_RETENTION = Duration.ofMinutes(5);
    private static final String MEMBER_SEPARATOR = "|";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${active-active.redis.enabled:false}")
    private boolean redisEnabled;

    public boolean isEnabled() {
        return redisEnabled;
    }

    public <T> void appendAll(
        String key,
        List<T> items,
        Function<T, String> memberIdExtractor,
        Function<T, Instant> timestampExtractor
    ) {
        if (!redisEnabled || items.isEmpty()) {
            return;
        }

        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = items.stream()
                .map(item -> toTuple(item, memberIdExtractor, timestampExtractor))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            if (!tuples.isEmpty()) {
                stringRedisTemplate.opsForZSet().add(key, tuples);
            }

            long cutoffEpochMillis = Instant.now().minus(DEFAULT_RETENTION).toEpochMilli();
            stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoffEpochMillis);
        } catch (RuntimeException exception) {
            log.warn("Redis recent-buffer append failed for {}: {}", key, exception.getMessage());
        }
    }

    public <T> List<T> getRecent(String key, int limit, Class<T> type) {
        if (!redisEnabled || limit <= 0) {
            return List.of();
        }

        try {
            Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(key, 0, limit - 1L);
            if (members == null || members.isEmpty()) {
                return List.of();
            }

            List<T> items = new ArrayList<>(members.size());
            for (String member : members) {
                T item = deserializeMember(member, type);
                if (item != null) {
                    items.add(item);
                }
            }

            Collections.reverse(items);
            return List.copyOf(items);
        } catch (RuntimeException exception) {
            log.warn("Redis recent-buffer read failed for {}: {}", key, exception.getMessage());
            return List.of();
        }
    }

    public int size(String key) {
        if (!redisEnabled) {
            return 0;
        }

        try {
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            if (size == null) {
                return 0;
            }
            return Math.toIntExact(Math.min(size, Integer.MAX_VALUE));
        } catch (RuntimeException exception) {
            log.warn("Redis recent-buffer size check failed for {}: {}", key, exception.getMessage());
            return 0;
        }
    }

    private <T> ZSetOperations.TypedTuple<String> toTuple(
        T item,
        Function<T, String> memberIdExtractor,
        Function<T, Instant> timestampExtractor
    ) {
        try {
            Instant timestamp = timestampExtractor.apply(item);
            if (timestamp == null) {
                return null;
            }

            return new DefaultTypedTuple<>(
                memberIdExtractor.apply(item) + MEMBER_SEPARATOR + objectMapper.writeValueAsString(item),
                (double) timestamp.toEpochMilli()
            );
        } catch (Exception exception) {
            log.warn("Failed to serialize Redis recent-buffer item: {}", exception.getMessage());
            return null;
        }
    }

    private <T> T deserializeMember(String member, Class<T> type) {
        try {
            int separatorIndex = member.indexOf(MEMBER_SEPARATOR);
            String payload = separatorIndex >= 0 ? member.substring(separatorIndex + 1) : member;
            return objectMapper.readValue(payload, type);
        } catch (Exception exception) {
            log.warn("Failed to deserialize Redis recent-buffer item for {}: {}", type.getSimpleName(), exception.getMessage());
            return null;
        }
    }
}
