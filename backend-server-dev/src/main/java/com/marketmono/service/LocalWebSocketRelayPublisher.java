package com.marketmono.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "websocket.relay.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalWebSocketRelayPublisher implements WebSocketRelayPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}