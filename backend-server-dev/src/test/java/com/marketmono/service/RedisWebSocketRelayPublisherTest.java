package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisWebSocketRelayPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisMessageListenerContainer redisMessageListenerContainer;

    private ObjectMapper objectMapper;
    private RedisWebSocketRelayPublisher relayPublisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        relayPublisher = new RedisWebSocketRelayPublisher(
            messagingTemplate,
            stringRedisTemplate,
            objectMapper,
            redisMessageListenerContainer
        );
    }

    @Test
    void shouldPublishLocallyAndRelayToRedis() {
        relayPublisher.publish("/topic/test", "payload");

        verify(messagingTemplate).convertAndSend("/topic/test", "payload");

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(channelCaptor.capture(), payloadCaptor.capture());

        assertThat(channelCaptor.getValue()).isEqualTo("ws-relay:/topic/test");
        assertThat(payloadCaptor.getValue()).contains("\"destination\":\"/topic/test\"");
        assertThat(payloadCaptor.getValue()).contains("\"payloadType\":\"java.lang.String\"");
    }

    @Test
    void shouldSubscribeUsingPatternTopic() {
        relayPublisher.subscribe();

        ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);
        verify(redisMessageListenerContainer).addMessageListener(eq(relayPublisher), topicCaptor.capture());

        assertThat(topicCaptor.getValue()).isInstanceOf(PatternTopic.class);
        assertThat(topicCaptor.getValue().getTopic()).isEqualTo("ws-relay:*");
    }

    @Test
    void shouldRelayMessagesFromOtherInstancesToLocalClients() throws Exception {
        String envelope = objectMapper.writeValueAsString(Map.of(
            "messageId", UUID.randomUUID().toString(),
            "instanceId", "other-instance",
            "destination", "/topic/option-trades",
            "payloadType", "java.lang.String",
            "payload", "broadcast"
        ));

        relayPublisher.onMessage(
            new DefaultMessage(
                "ws-relay:/topic/option-trades".getBytes(StandardCharsets.UTF_8),
                envelope.getBytes(StandardCharsets.UTF_8)
            ),
            null
        );

        verify(messagingTemplate).convertAndSend("/topic/option-trades", "broadcast");
    }

    @Test
    void shouldIgnoreMessagesFromSameInstance() throws Exception {
        String sameInstanceId = instanceId();
        String envelope = objectMapper.writeValueAsString(Map.of(
            "messageId", UUID.randomUUID().toString(),
            "instanceId", sameInstanceId,
            "destination", "/topic/flow-alerts",
            "payloadType", "java.lang.String",
            "payload", "echo"
        ));

        relayPublisher.onMessage(
            new DefaultMessage(
                "ws-relay:/topic/flow-alerts".getBytes(StandardCharsets.UTF_8),
                envelope.getBytes(StandardCharsets.UTF_8)
            ),
            null
        );

        verify(messagingTemplate, never()).convertAndSend(anyString(), eq("echo"));
    }

    private String instanceId() throws Exception {
        Field field = RedisWebSocketRelayPublisher.class.getDeclaredField("instanceId");
        field.setAccessible(true);
        return (String) field.get(relayPublisher);
    }
}