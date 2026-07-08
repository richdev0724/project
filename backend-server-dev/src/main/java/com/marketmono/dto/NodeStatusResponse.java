package com.marketmono.dto;

import java.time.Instant;
import java.util.List;

public record NodeStatusResponse(
    String nodeId,
    String instanceUuid,
    Instant startedAt,
    long uptimeSeconds,
    List<String> profiles,
    KafkaStatus kafka,
    WebSocketStatus websocket,
    RedisStatus redis,
    DatabaseStatus database,
    ShedLockStatus shedlock
) {

    public record KafkaStatus(
        boolean enabled,
        int listenersRunning,
        int listenersTotal,
        List<String> partitionsAssigned
    ) {
    }

    public record WebSocketStatus(
        boolean relayEnabled,
        String relayType,
        int activeSessions
    ) {
    }

    public record RedisStatus(
        boolean connected,
        boolean cacheEnabled
    ) {
    }

    public record DatabaseStatus(
        int activeConnections,
        int maxPoolSize,
        int pendingAcquire
    ) {
    }

    public record ShedLockStatus(
        List<String> activeLocksHeld
    ) {
    }
}