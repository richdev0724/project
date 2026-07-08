package com.marketmono.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "websocket.relay.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RedisWebSocketRelayPublisher implements WebSocketRelayPublisher, MessageListener {

    private static final String CHANNEL_PATTERN = "ws-relay:*";
    private static final String CHANNEL_PREFIX = "ws-relay:";

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    private final com.github.benmanes.caffeine.cache.Cache<String, Boolean> processedMessageIds = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(10_000)
        .build();

    private final String instanceId = UUID.randomUUID().toString();
    private final PatternTopic patternTopic = new PatternTopic(CHANNEL_PATTERN);

    @PostConstruct
    void subscribe() {
        redisMessageListenerContainer.addMessageListener(this, patternTopic);
        log.info("WebSocket Redis relay subscribed on {} with instanceId={}", CHANNEL_PATTERN, instanceId);
    }

    @PreDestroy
    void unsubscribe() {
        redisMessageListenerContainer.removeMessageListener(this);
    }

    @Override
    public void publish(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);

        try {
            String messageId = UUID.randomUUID().toString();
            processedMessageIds.put(messageId, Boolean.TRUE);

            RelayEnvelope envelope = new RelayEnvelope(
                messageId,
                instanceId,
                destination,
                payload.getClass().getName(),
                objectMapper.valueToTree(payload)
            );

            stringRedisTemplate.convertAndSend(channelFor(destination), objectMapper.writeValueAsString(envelope));
        } catch (Exception exception) {
            log.warn("Failed to publish WebSocket relay message for {}: {}", destination, exception.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RelayEnvelope envelope = objectMapper.readValue(message.getBody(), RelayEnvelope.class);

            if (instanceId.equals(envelope.instanceId()) || processedMessageIds.getIfPresent(envelope.messageId()) != null) {
                return;
            }

            processedMessageIds.put(envelope.messageId(), Boolean.TRUE);
            Class<?> payloadType = Class.forName(envelope.payloadType());
            Object payload = objectMapper.treeToValue(envelope.payload(), payloadType);
            messagingTemplate.convertAndSend(envelope.destination(), payload);
        } catch (Exception exception) {
            String messageBody = new String(message.getBody(), StandardCharsets.UTF_8);
            log.warn("Failed to consume WebSocket relay message {}: {}", messageBody, exception.getMessage());
        }
    }

    private String channelFor(String destination) {
        return CHANNEL_PREFIX + destination;
    }

    private record RelayEnvelope(
        String messageId,
        String instanceId,
        String destination,
        String payloadType,
        JsonNode payload
    ) {
    }
}